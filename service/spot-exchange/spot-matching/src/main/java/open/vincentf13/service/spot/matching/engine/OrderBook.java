package open.vincentf13.service.spot.matching.engine;

import open.vincentf13.service.spot.model.Order;
import org.agrona.collections.Long2ObjectHashMap;
import java.util.*;

/** 
 內存訂單簿 (OrderBook) - 數據結構優化版
 
 優化點：
 1. 消除 LinkedList：改用 ArrayDeque (基於循環數組)，大幅提升 Cache 命中率並消除 Node 物件分配。
 2. 消除裝箱：使用 Agrona 的 Long2ObjectHashMap 進行 OrderId 索引，實現零裝箱、高性能查詢。
 3. 性能導向：TreeMap 負責排序，ArrayDeque 負責時間優先隊列，實現 O(1) 的隊列操作。
 */
public class OrderBook {
    private final int symbolId;
    
    /** 買單層級 (降序)：價格 -> 訂單循環數組 */
    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    
    /** 賣單層級 (升序)：價格 -> 訂單循環數組 */
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();
    
    /** 
      快速查找索引：OrderId -> Order 指標
      採用 Agrona Long2ObjectHashMap 避免 Long 物件裝箱開銷
     */
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(); 

    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
    }

    @FunctionalInterface
    public interface TradeHandler {
        void onTrade(long makerUserId, long takerUserId, long price, long qty, long makerOrderId);
    }

    /** 執行撮合 */
    public void match(Order taker, TradeHandler handler) {
        boolean isBuy = taker.getSide() == 0;
        TreeMap<Long, Deque<Order>> counterSide = isBuy ? asks : bids;

        while (!counterSide.isEmpty() && taker.getQty() > taker.getFilled()) {
            Map.Entry<Long, Deque<Order>> bestLevel = counterSide.firstEntry();
            long bestPrice = bestLevel.getKey();

            if (isBuy ? (taker.getPrice() < bestPrice) : (taker.getPrice() > bestPrice)) break;

            Deque<Order> makers = bestLevel.getValue();
            
            // 使用 ArrayDeque 的 peek/poll 進行高效處理
            while (!makers.isEmpty() && taker.getQty() > taker.getFilled()) {
                Order maker = makers.peekFirst();
                long matchQty = Math.min(taker.getQty() - taker.getFilled(), maker.getQty() - maker.getFilled());

                handler.onTrade(maker.getUserId(), taker.getUserId(), bestPrice, matchQty, maker.getOrderId());

                taker.setFilled(taker.getFilled() + matchQty);
                maker.setFilled(maker.getFilled() + matchQty);

                if (maker.getFilled() == maker.getQty()) {
                    makers.pollFirst(); // 移除已完全成交的 Maker
                    orderIndex.remove(maker.getOrderId());
                }
            }
            
            // 價格位準排空，移除節點
            if (makers.isEmpty()) counterSide.pollFirstEntry();
        }

        if (taker.getQty() > taker.getFilled()) add(taker);
    }

    /** 加入訂單 */
    public void add(Order order) {
        TreeMap<Long, Deque<Order>> levels = (order.getSide() == 0) ? bids : asks;
        // 使用 ArrayDeque 取代 LinkedList，減少物件分配
        levels.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>()).addLast(order);
        orderIndex.put(order.getOrderId(), order);
    }

    /** 移除訂單 */
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
