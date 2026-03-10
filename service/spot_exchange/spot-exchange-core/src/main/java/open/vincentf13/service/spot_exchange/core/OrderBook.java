package open.vincentf13.service.spot_exchange.core;

import open.vincentf13.service.spot_exchange.model.ActiveOrder;
import java.util.*;

/** 
  內存訂單簿
  實作價格優先、時間優先撮合算法
 */
public class OrderBook {
    private final int symbolId;
    private final TreeMap<Long, LinkedList<ActiveOrder>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, LinkedList<ActiveOrder>> asks = new TreeMap<>();

    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
    }

    /** 
      嘗試撮合新訂單
      @return 撮合產生的成交記錄 (Trade)
     */
    public List<TradeEvent> match(ActiveOrder newOrder) {
        List<TradeEvent> trades = new ArrayList<>();
        boolean isBuy = newOrder.getSide() == 0;
        TreeMap<Long, LinkedList<ActiveOrder>> counterSide = isBuy ? asks : bids;

        while (newOrder.getQty() > newOrder.getFilled() && !counterSide.isEmpty()) {
            Map.Entry<Long, LinkedList<ActiveOrder>> bestEntry = counterSide.firstEntry();
            long bestPrice = bestEntry.getKey();

            // 檢查價格是否匹配
            if (isBuy ? (newOrder.getPrice() < bestPrice) : (newOrder.getPrice() > bestPrice)) {
                break;
            }

            LinkedList<ActiveOrder> ordersAtPrice = bestEntry.getValue();
            Iterator<ActiveOrder> iterator = ordersAtPrice.iterator();

            while (iterator.hasNext() && newOrder.getQty() > newOrder.getFilled()) {
                ActiveOrder makerOrder = iterator.next();
                long matchQty = Math.min(newOrder.getQty() - newOrder.getFilled(), 
                                         makerOrder.getQty() - makerOrder.getFilled());

                // 產生撮合事件
                trades.add(new TradeEvent(makerOrder.getUserId(), newOrder.getUserId(), 
                                         bestPrice, matchQty, makerOrder.getOrderId()));

                // 更新成交量
                newOrder.setFilled(newOrder.getFilled() + matchQty);
                makerOrder.setFilled(makerOrder.getFilled() + matchQty);

                if (makerOrder.getFilled() == makerOrder.getQty()) {
                    iterator.remove();
                }
            }

            if (ordersAtPrice.isEmpty()) {
                counterSide.pollFirstEntry();
            }
        }

        // 若未完全成交，掛入買賣盤
        if (newOrder.getQty() > newOrder.getFilled()) {
            add(newOrder);
        }

        return trades;
    }

    public void add(ActiveOrder order) {
        TreeMap<Long, LinkedList<ActiveOrder>> sideMap = (order.getSide() == 0) ? bids : asks;
        sideMap.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).add(order);
    }

    public Optional<ActiveOrder> findOrder(long orderId) {
        for (LinkedList<ActiveOrder> orders : bids.values()) {
            for (ActiveOrder o : orders) if (o.getOrderId() == orderId) return Optional.of(o);
        }
        for (LinkedList<ActiveOrder> orders : asks.values()) {
            for (ActiveOrder o : orders) if (o.getOrderId() == orderId) return Optional.of(o);
        }
        return Optional.empty();
    }

    public void remove(ActiveOrder order) {
        TreeMap<Long, LinkedList<ActiveOrder>> sideMap = (order.getSide() == 0) ? bids : asks;
        LinkedList<ActiveOrder> ordersAtPrice = sideMap.get(order.getPrice());
        if (ordersAtPrice != null) {
            ordersAtPrice.removeIf(o -> o.getOrderId() == order.getOrderId());
            if (ordersAtPrice.isEmpty()) {
                sideMap.remove(order.getPrice());
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
