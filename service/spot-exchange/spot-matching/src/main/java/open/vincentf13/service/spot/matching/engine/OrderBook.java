package open.vincentf13.service.spot.matching.engine;

import open.vincentf13.service.spot.model.Order;
import org.agrona.collections.Long2ObjectHashMap;
import java.util.*;

/** 
 內存訂單簿 (OrderBook) - $O(1)$ 撤單優化版
 
 優化點：
 1. 延遲刪除 (Lazy Deletion)：撤單時僅移除索引，不遍歷隊列。在撮合掃描時自動剔除失效訂單。
 2. 撤單效能：將撤單複雜度從 $O(N)$ 降至 $O(1)$。
 */
public class OrderBook {
    private static final int INITIAL_DEQUE_CAPACITY = 1024;
    private static final int INITIAL_ORDER_MAP_CAPACITY = 100_000;
    private static final float LOAD_FACTOR = 0.5f;

    private final int symbolId;
    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(INITIAL_ORDER_MAP_CAPACITY, LOAD_FACTOR); 

    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
    }

    @FunctionalInterface
    public interface TradeHandler {
        void onTrade(long makerUserId, long takerUserId, long price, long qty, long makerOrderId);
    }

    public void match(Order taker, TradeHandler handler) {
        final boolean isBuy = taker.getSide() == 0;
        final TreeMap<Long, Deque<Order>> counterSide = isBuy ? asks : bids;
        final long takerPrice = taker.getPrice();

        while (taker.getQty() > taker.getFilled()) {
            Map.Entry<Long, Deque<Order>> bestLevelEntry = counterSide.firstEntry();
            if (bestLevelEntry == null) break;

            final long bestPrice = bestLevelEntry.getKey();
            if (isBuy ? (takerPrice < bestPrice) : (takerPrice > bestPrice)) break;

            final Deque<Order> makers = bestLevelEntry.getValue();
            
            while (!makers.isEmpty() && taker.getQty() > taker.getFilled()) {
                Order maker = makers.peekFirst();
                
                // --- 延遲刪除清理邏輯 ---
                // 如果 Maker 已經不在索引中，代表該訂單已被撤單或已無效，直接跳過
                if (!orderIndex.containsKey(maker.getOrderId())) {
                    makers.pollFirst();
                    continue;
                }

                long matchQty = Math.min(taker.getQty() - taker.getFilled(), maker.getQty() - maker.getFilled());
                handler.onTrade(maker.getUserId(), taker.getUserId(), bestPrice, matchQty, maker.getOrderId());

                taker.setFilled(taker.getFilled() + matchQty);
                maker.setFilled(maker.getFilled() + matchQty);

                if (maker.getFilled() == maker.getQty()) {
                    makers.pollFirst(); 
                    orderIndex.remove(maker.getOrderId());
                }
            }
            
            if (makers.isEmpty()) counterSide.pollFirstEntry();
            else break;
        }

        if (taker.getQty() > taker.getFilled()) add(taker);
    }

    public void add(Order order) {
        TreeMap<Long, Deque<Order>> levels = (order.getSide() == 0) ? bids : asks;
        levels.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>(INITIAL_DEQUE_CAPACITY)).addLast(order);
        orderIndex.put(order.getOrderId(), order);
    }

    /** 
      撤單操作：$O(1)$ 
      僅移除索引指標，具體內存隊列的清理交由 match 循環或背景任務處理
     */
    public void remove(long orderId) {
        orderIndex.remove(orderId);
    }
}
