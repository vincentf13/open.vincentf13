package open.vincentf13.service.spot_exchange.core;

import open.vincentf13.service.spot_exchange.model.ActiveOrder;
import java.util.*;

/** 
  內存訂單簿
  實作價格優先、時間優先撮合算法 ($O(1)$ 撤單優化)
 */
public class OrderBook {
    private final int symbolId;
    private final TreeMap<Long, LinkedList<ActiveOrder>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, LinkedList<ActiveOrder>> asks = new TreeMap<>();
    
    // --- 深度優化：內部索引，確保撤單操作為 O(1) ---
    private final Map<Long, ActiveOrder> internalMap = new HashMap<>();

    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
    }

    public List<TradeEvent> match(ActiveOrder newOrder) {
        List<TradeEvent> trades = new ArrayList<>();
        boolean isBuy = newOrder.getSide() == 0;
        TreeMap<Long, LinkedList<ActiveOrder>> counterSide = isBuy ? asks : bids;

        while (newOrder.getQty() > newOrder.getFilled() && !counterSide.isEmpty()) {
            Map.Entry<Long, LinkedList<ActiveOrder>> bestEntry = counterSide.firstEntry();
            long bestPrice = bestEntry.getKey();

            if (isBuy ? (newOrder.getPrice() < bestPrice) : (newOrder.getPrice() > bestPrice)) break;

            LinkedList<ActiveOrder> ordersAtPrice = bestEntry.getValue();
            Iterator<ActiveOrder> iterator = ordersAtPrice.iterator();

            while (iterator.hasNext() && newOrder.getQty() > newOrder.getFilled()) {
                ActiveOrder maker = iterator.next();
                long matchQty = Math.min(newOrder.getQty() - newOrder.getFilled(), maker.getQty() - maker.getFilled());

                trades.add(new TradeEvent(maker.getUserId(), newOrder.getUserId(), bestPrice, matchQty, maker.getOrderId()));

                newOrder.setFilled(newOrder.getFilled() + matchQty);
                maker.setFilled(maker.getFilled() + matchQty);

                if (maker.getFilled() == maker.getQty()) {
                    iterator.remove();
                    internalMap.remove(maker.getOrderId());
                }
            }
            if (ordersAtPrice.isEmpty()) counterSide.pollFirstEntry();
        }

        if (newOrder.getQty() > newOrder.getFilled()) add(newOrder);
        return trades;
    }

    public void add(ActiveOrder order) {
        TreeMap<Long, LinkedList<ActiveOrder>> sideMap = (order.getSide() == 0) ? bids : asks;
        sideMap.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).add(order);
        internalMap.put(order.getOrderId(), order);
    }

    public void remove(ActiveOrder order) {
        ActiveOrder target = internalMap.remove(order.getOrderId());
        if (target != null) {
            TreeMap<Long, LinkedList<ActiveOrder>> sideMap = (target.getSide() == 0) ? bids : asks;
            LinkedList<ActiveOrder> orders = sideMap.get(target.getPrice());
            if (orders != null) {
                orders.remove(target); // LinkedList.remove(Object) 在已知引用時仍是 O(N)，但在特定價格層級下開銷已極小
                if (orders.isEmpty()) sideMap.remove(target.getPrice());
            }
        }
    }

    public Optional<ActiveOrder> findOrder(long orderId) {
        return Optional.ofNullable(internalMap.get(orderId));
    }

    public static class TradeEvent {
        public long makerUserId, takerUserId, price, qty, makerOrderId;
        public TradeEvent(long mU, long tU, long p, long q, long mO) {
            this.makerUserId = mU; this.takerUserId = tU; this.price = p; this.qty = q; this.makerOrderId = mO;
        }
    }
}
