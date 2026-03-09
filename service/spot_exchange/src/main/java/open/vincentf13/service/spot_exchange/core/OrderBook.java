package open.vincentf13.service.spot_exchange.core;

import open.vincentf13.service.spot_exchange.model.ActiveOrder;
import java.util.*;

/** 
  內存訂單簿
  維護單個交易對的 L2 掛單狀態
 */
public class OrderBook {
    private final int symbolId;
    
    // 價格從高到低 (Bids)
    private final TreeMap<Long, List<ActiveOrder>> bids = new TreeMap<>(Collections.reverseOrder());
    // 價格從低到高 (Asks)
    private final TreeMap<Long, List<ActiveOrder>> asks = new TreeMap<>();

    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
    }

    public void add(ActiveOrder order) {
        TreeMap<Long, List<ActiveOrder>> sideMap = (order.getSide() == 0) ? bids : asks;
        sideMap.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).add(order);
    }

    public TreeMap<Long, List<ActiveOrder>> getBids() { return bids; }
    public TreeMap<Long, List<ActiveOrder>> getAsks() { return asks; }
}
