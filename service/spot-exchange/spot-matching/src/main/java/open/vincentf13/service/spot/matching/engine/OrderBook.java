package open.vincentf13.service.spot.matching.engine;

import open.vincentf13.service.spot.model.Order;
import java.util.*;

/** 
 內存訂單簿 (OrderBook)
 職責：執行「價格優先、時間優先 (Price-Time Priority)」撮合算法
 特性：
 1. 高性能存儲：採用 TreeMap 維護價格層級，確保價格排序效率。
 2. 確定性撮合：嚴格遵循到達順序，保證撮合結果的可預測性。
 */
public class OrderBook {
    private final int symbolId;
    // 買單簿：價格由高到低排序
    private final TreeMap<Long, LinkedList<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    // 賣單簿：價格由低到高排序
    private final TreeMap<Long, LinkedList<Order>> asks = new TreeMap<>();
    // 全局訂單索引，用於快速定位
    private final Map<Long, Order> internalMap = new HashMap<>(); 

    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
    }

    /** 
      執行指令撮合
      @param newOrder 進入系統的新訂單 (Taker)
      @return 成交事件列表
     */
    public List<TradeEvent> match(Order newOrder) {
        List<TradeEvent> trades = new ArrayList<>();
        boolean isBuy = newOrder.getSide() == 0;
        TreeMap<Long, LinkedList<Order>> counterSide = isBuy ? asks : bids;

        // 當新訂單還有剩餘數量且對向簿不為空時，持續嘗試撮合
        while (newOrder.getQty() > newOrder.getFilled() && !counterSide.isEmpty()) {
            Map.Entry<Long, LinkedList<Order>> bestEntry = counterSide.firstEntry();
            long bestPrice = bestEntry.getKey();

            // 檢查價格重疊 (Price Improvement Check)
            // 買單：若新訂單價格 < 簿中最優賣價，停止撮合
            // 賣單：若新訂單價格 > 簿中最優買價，停止撮合
            if (isBuy ? (newOrder.getPrice() < bestPrice) : (newOrder.getPrice() > bestPrice)) break;

            LinkedList<Order> ordersAtPrice = bestEntry.getValue();
            Iterator<Order> iterator = ordersAtPrice.iterator();

            while (iterator.hasNext() && newOrder.getQty() > newOrder.getFilled()) {
                Order maker = iterator.next();
                long matchQty = Math.min(newOrder.getQty() - newOrder.getFilled(), maker.getQty() - maker.getFilled());

                // 產生紀錄事件：成交價採「簿中已有的最優價格 (Maker Price)」
                trades.add(new TradeEvent(maker.getUserId(), newOrder.getUserId(), bestPrice, matchQty, maker.getOrderId()));

                // 更新成交進度
                newOrder.setFilled(newOrder.getFilled() + matchQty);
                maker.setFilled(maker.getFilled() + matchQty);

                // 若 Maker 訂單完全成交，從簿中移除
                if (maker.getFilled() == maker.getQty()) {
                    iterator.remove();
                    internalMap.remove(maker.getOrderId());
                }
            }
            
            // 若該價格層級已無掛單，移除該價格節點
            if (ordersAtPrice.isEmpty()) counterSide.pollFirstEntry();
        }

        // 若新訂單未完全成交且非 IOC/FOK，則加入訂單簿成為 Maker
        if (newOrder.getQty() > newOrder.getFilled()) add(newOrder);
        
        return trades;
    }

    /** 
      將掛單加入訂單簿
     */
    public void add(Order order) {
        TreeMap<Long, LinkedList<Order>> sideMap = (order.getSide() == 0) ? bids : asks;
        sideMap.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).add(order);
        internalMap.put(order.getOrderId(), order);
    }

    /** 
      根據 ID 查找簿中訂單
     */
    public Optional<Order> findOrder(long orderId) {
        return Optional.ofNullable(internalMap.get(orderId));
    }

    /** 
      成交事件數據載體
     */
    public static class TradeEvent {
        public long makerUserId, takerUserId, price, qty, makerOrderId;
        public TradeEvent(long mU, long tU, long p, long q, long mO) {
            this.makerUserId = mU; 
            this.takerUserId = tU; 
            this.price = p; 
            this.qty = q; 
            this.makerOrderId = mO;
        }
    }
}
