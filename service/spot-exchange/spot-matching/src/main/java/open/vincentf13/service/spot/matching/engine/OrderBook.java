package open.vincentf13.service.spot.matching.engine;

import open.vincentf13.service.spot.model.Order;
import org.agrona.collections.Long2ObjectHashMap;
import java.util.*;

/** 
 內存訂單簿 (OrderBook)
 職責：執行撮合算法，透過連續內存佈局（Cache-friendly）優化 L2 盤口處理性能
 */
public class OrderBook {
    private static final int INITIAL_DEQUE_CAPACITY = 1024;
    private static final int INITIAL_ORDER_MAP_CAPACITY = 100_000;
    private static final float LOAD_FACTOR = 0.5f;

    private final int symbolId;
    
    /** 買單層級：TreeMap 提供價格排序，Deque (循環數組) 提供 O(1) 插入與 CPU 友善的連續掃描 */
    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    
    /** 賣單層級：升序排列 */
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();
    
    /** 快速查找索引：OrderId -> Order 指標，採用 Agrona 原始類型映射，徹底消除 Long 裝箱開銷 */
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(INITIAL_ORDER_MAP_CAPACITY, LOAD_FACTOR); 

    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
    }

    /** 成交處理回調：消除 List<TradeEvent> 分配，實現撮合即結算 */
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
                long matchQty = Math.min(taker.getQty() - taker.getFilled(), maker.getQty() - maker.getFilled());

                handler.onTrade(maker.getUserId(), taker.getUserId(), bestPrice, matchQty, maker.getOrderId());

                taker.setFilled(taker.getFilled() + matchQty);
                maker.setFilled(maker.getFilled() + matchQty);

                if (maker.getFilled() == maker.getQty()) {
                    makers.pollFirst(); 
                    orderIndex.remove(maker.getOrderId());
                }
            }
            
            if (makers.isEmpty()) {
                counterSide.pollFirstEntry();
            } else break;
        }

        if (taker.getQty() > taker.getFilled()) add(taker);
    }

    public void add(Order order) {
        TreeMap<Long, Deque<Order>> levels = (order.getSide() == 0) ? bids : asks;
        levels.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>(INITIAL_DEQUE_CAPACITY)).addLast(order);
        orderIndex.put(order.getOrderId(), order);
    }

    public void remove(long orderId) {
        Order o = orderIndex.remove(orderId);
        if (o != null) {
            TreeMap<Long, Deque<Order>> levels = (o.getSide() == 0) ? bids : asks;
            Deque<Order> list = levels.get(o.getPrice());
            if (list != null) {
                list.remove(o);
                if (list.isEmpty()) levels.remove(o.getPrice());
            }
        }
    }
}
