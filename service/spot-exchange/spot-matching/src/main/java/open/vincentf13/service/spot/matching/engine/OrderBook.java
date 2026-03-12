package open.vincentf13.service.spot.matching.engine;

import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import org.agrona.collections.Long2ObjectHashMap;
import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** 
 內存訂單簿 (OrderBook)
 職責：符號級狀態機，自主管理撮合邏輯、數據持久化與狀態恢復
 */
public class OrderBook {
    private static final int INITIAL_DEQUE_CAPACITY = 4096;
    private static final int INITIAL_ORDER_MAP_CAPACITY = 200_000;
    private static final float LOAD_FACTOR = 0.5f;

    private final int symbolId;
    
    // --- 數據結構下沉：由 OrderBook 實例持有並管理其領域內的持久化指標 ---
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();
    private final ChronicleMap<Long, Boolean> activeOrderIdDiskMap = Storage.self().activeOrders();
    private final ChronicleMap<Long, Trade> tradeHistoryDiskMap = Storage.self().trades();

    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(INITIAL_ORDER_MAP_CAPACITY, LOAD_FACTOR); 

    private final Trade reusableTrade = new Trade();

    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
    }

    /** 
      靜態恢復引導：封裝全局掃描邏輯，將活躍訂單路由至正確的 OrderBook 實例
     */
    public static void rebuildAll(IntFunction<OrderBook> bookFinder) {
        ChronicleMap<Long, Boolean> activeIds = Storage.self().activeOrders();
        ChronicleMap<Long, Order> allOrders = Storage.self().orders();
        
        activeIds.forEach((id, active) -> {
            Order o = allOrders.getUsing(id, new Order());
            if (o != null && o.getStatus() < 2) {
                bookFinder.apply(o.getSymbolId()).add(o);
            }
        });
    }

    @FunctionalInterface public interface TradeHandler {
        void onTrade(Order maker, long price, long qty);
    }

    public void match(Order taker, long gwSeq, long timestamp, Supplier<Long> tradeIdSupplier, TradeHandler handler) {
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
                if (!orderIndex.containsKey(maker.getOrderId())) {
                    makers.pollFirst(); continue;
                }

                long matchQty = Math.min(taker.getQty() - taker.getFilled(), maker.getQty() - maker.getFilled());
                
                long tid = tradeIdSupplier.get();
                reusableTrade.setTradeId(tid); reusableTrade.setOrderId(maker.getOrderId());
                reusableTrade.setPrice(bestPrice); reusableTrade.setQty(matchQty);
                reusableTrade.setTime(timestamp); reusableTrade.setLastSeq(gwSeq);
                tradeHistoryDiskMap.put(tid, reusableTrade);

                maker.setFilled(maker.getFilled() + matchQty);
                taker.setFilled(taker.getFilled() + matchQty);
                handler.onTrade(maker, bestPrice, matchQty);

                if (maker.getFilled() == maker.getQty()) {
                    syncOrder(maker, gwSeq);
                    makers.pollFirst(); orderIndex.remove(maker.getOrderId());
                } else {
                    syncOrder(maker, gwSeq);
                    break;
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
      狀態同步：封裝訂單持久化與活躍狀態維護邏輯
     */
    public void syncOrder(Order o, long gwSeq) {
        if (o.getFilled() == o.getQty()) o.setStatus((byte) 2);
        else if (o.getFilled() > 0) o.setStatus((byte) 1);
        
        o.setVersion(o.getVersion() + 1);
        o.setLastSeq(gwSeq);
        
        allOrdersDiskMap.put(o.getOrderId(), o);
        if (o.getStatus() < 2) activeOrderIdDiskMap.put(o.getOrderId(), true);
        else activeOrderIdDiskMap.remove(o.getOrderId());
    }

    public void remove(long orderId) { orderIndex.remove(orderId); }
}
