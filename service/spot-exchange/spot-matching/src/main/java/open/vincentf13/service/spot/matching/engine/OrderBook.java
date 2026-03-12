package open.vincentf13.service.spot.matching.engine;

import open.vincentf13.service.spot.model.Order;
import java.util.*;

/** 
 內存訂單簿 (OrderBook)
 職責：執行「價格優先、時間優先」撮合算法，維護 L2 價格層級
 */
public class OrderBook {
    private final int symbolId;
    
    /** 買單隊列：按價格從高到低排列 (TreeMap)，同一價格按時間先後排列 (LinkedList) */
    private final TreeMap<Long, LinkedList<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    
    /** 賣單隊列：按價格從低到高排列 (TreeMap)，同一價格按時間先後排列 (LinkedList) */
    private final TreeMap<Long, LinkedList<Order>> asks = new TreeMap<>();
    
    /** 訂單查找表：OrderId -> Order 指標，用於快速定位、更新或移除簿中的 Maker 訂單 */
    private final Map<Long, Order> orderIndex = new HashMap<>(); 

    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
    }

    public List<TradeEvent> match(Order taker) {
        List<TradeEvent> trades = new ArrayList<>();
        boolean isBuy = taker.getSide() == 0;
        TreeMap<Long, LinkedList<Order>> counterSide = isBuy ? asks : bids;

        while (!counterSide.isEmpty() && taker.getQty() > taker.getFilled()) {
            Map.Entry<Long, LinkedList<Order>> bestLevel = counterSide.firstEntry();
            long bestPrice = bestLevel.getKey();

            if (isBuy ? (taker.getPrice() < bestPrice) : (taker.getPrice() > bestPrice)) break;

            LinkedList<Order> makers = bestLevel.getValue();
            Iterator<Order> it = makers.iterator();

            while (it.hasNext() && taker.getQty() > taker.getFilled()) {
                Order maker = it.next();
                long matchQty = Math.min(taker.getQty() - taker.getFilled(), maker.getQty() - maker.getFilled());

                trades.add(new TradeEvent(maker.getUserId(), taker.getUserId(), bestPrice, matchQty, maker.getOrderId()));

                taker.setFilled(taker.getFilled() + matchQty);
                maker.setFilled(maker.getFilled() + matchQty);

                if (maker.getFilled() == maker.getQty()) {
                    it.remove();
                    orderIndex.remove(maker.getOrderId());
                }
            }
            
            if (makers.isEmpty()) counterSide.pollFirstEntry();
        }

        if (taker.getQty() > taker.getFilled()) add(taker);
        
        return trades;
    }

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
