package open.vincentf13.service.spot.matching.engine;

import open.vincentf13.service.spot.model.Order;
import java.util.*;

/** 
 內存訂單簿 (OrderBook)
 職責：執行「價格優先、時間優先」撮合算法，並維護 L2 價格層級
 
 撮合核心邏輯：
 1. 價格匹配：買單價格 >= 最優賣價，或賣單價格 <= 最優買價。
 2. 數量匹配：取兩端剩餘數量的最小值執行成交。
 3. 價格改進：成交價採 Maker (簿中已有訂單) 的價格，保護 Maker 利益並實現 Taker 價格改善。
 */
public class OrderBook {
    private final int symbolId;
    // 買方層級 (價格從高到低)
    private final TreeMap<Long, LinkedList<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    // 賣方層級 (價格從低到高)
    private final TreeMap<Long, LinkedList<Order>> asks = new TreeMap<>();
    // 快速索引：OrderId -> Order (用於撤單或狀態查詢)
    private final Map<Long, Order> orderIndex = new HashMap<>(); 

    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
    }

    /** 
      執行 Taker 訂單撮合
      @param taker 進入系統的新訂單
      @return 本次觸發的成交事件列表
     */
    public List<TradeEvent> match(Order taker) {
        List<TradeEvent> trades = new ArrayList<>();
        boolean isBuy = taker.getSide() == 0;
        TreeMap<Long, LinkedList<Order>> counterSide = isBuy ? asks : bids;

        // 當 Taker 還有剩餘數量且對面簿不為空時，嘗試撮合
        while (!counterSide.isEmpty() && taker.getQty() > taker.getFilled()) {
            Map.Entry<Long, LinkedList<Order>> bestLevel = counterSide.firstEntry();
            long bestPrice = bestLevel.getKey();

            // 價格不再重疊，停止撮合
            if (isBuy ? (taker.getPrice() < bestPrice) : (taker.getPrice() > bestPrice)) break;

            LinkedList<Order> makers = bestLevel.getValue();
            Iterator<Order> it = makers.iterator();

            while (it.hasNext() && taker.getQty() > taker.getFilled()) {
                Order maker = it.next();
                long matchQty = Math.min(taker.getQty() - taker.getFilled(), maker.getQty() - maker.getFilled());

                // 產生紀錄事件
                trades.add(new TradeEvent(maker.getUserId(), taker.getUserId(), bestPrice, matchQty, maker.getOrderId()));

                // 更新成交狀態
                taker.setFilled(taker.getFilled() + matchQty);
                maker.setFilled(maker.getFilled() + matchQty);

                // Maker 完全成交，移除索引與節點
                if (maker.getFilled() == maker.getQty()) {
                    it.remove();
                    orderIndex.remove(maker.getOrderId());
                }
            }
            
            // 如果該價格層級已排空，移除節點
            if (makers.isEmpty()) counterSide.pollFirstEntry();
        }

        // 若 Taker 還有剩餘，轉為 Maker 加入訂單簿
        if (taker.getQty() > taker.getFilled()) add(taker);
        
        return trades;
    }

    /** 將訂單加入簿中 (成為 Maker) */
    public void add(Order order) {
        TreeMap<Long, LinkedList<Order>> levels = (order.getSide() == 0) ? bids : asks;
        levels.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).add(order);
        orderIndex.put(order.getOrderId(), order);
    }

    public void remove(long orderId) {
        Order o = orderIndex.remove(orderId);
        if (o != null) {
            TreeMap<Long, LinkedList<Order>> levels = (o.getSide() == 0) ? bids : asks;
            LinkedList<Order> list = levels.get(o.getPrice());
            if (list != null) {
                list.remove(o);
                if (list.isEmpty()) levels.remove(o.getPrice());
            }
        }
    }

    public static class TradeEvent {
        public long makerUserId, takerUserId, price, qty, makerOrderId;
        public TradeEvent(long mU, long tU, long p, long q, long mO) {
            this.makerUserId = mU; this.takerUserId = tU; this.price = p; this.qty = q; this.makerOrderId = mO;
        }
    }
}
