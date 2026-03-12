package open.vincentf13.service.spot.matching.engine;

import open.vincentf13.service.spot.model.Order;
import java.util.*;

/** 
 內存訂單簿 (OrderBook) - 性能優化版
 職責：執行撮合算法，實現零對象分配的成交處理
 */
public class OrderBook {
    private final int symbolId;
    private final TreeMap<Long, LinkedList<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, LinkedList<Order>> asks = new TreeMap<>();
    private final Map<Long, Order> orderIndex = new HashMap<>(); 

    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
    }

    /** 
      成交處理回調接口：避免在撮合循環中建立 List<TradeEvent>
     */
    @FunctionalInterface
    public interface TradeHandler {
        void onTrade(long makerUserId, long takerUserId, long price, long qty, long makerOrderId);
    }

    /** 
      執行撮合 (回調模式)
     */
    public void match(Order taker, TradeHandler handler) {
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

                // 直接回調業務邏輯，不建立事件物件
                handler.onTrade(maker.getUserId(), taker.getUserId(), bestPrice, matchQty, maker.getOrderId());

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
}
