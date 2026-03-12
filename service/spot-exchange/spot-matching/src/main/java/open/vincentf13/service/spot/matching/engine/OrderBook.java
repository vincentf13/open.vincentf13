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
 內存訂單簿 (OrderBook) - Zero-GC 物件池優化版
 職責：自主管理撮合邏輯與物件生命週期，徹底消除熱點路徑的 new 操作
 */
public class OrderBook {
    private static final int INITIAL_DEQUE_CAPACITY = 4096;
    private static final int POOL_SIZE = 100_000; // 預分配物件池大小

    private final int symbolId;
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();
    private final ChronicleMap<Long, Boolean> activeOrderIdDiskMap = Storage.self().activeOrders();
    private final ChronicleMap<Long, Trade> tradeHistoryDiskMap = Storage.self().trades();

    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(200_000, 0.5f); 

    /** 物件池：循環利用 Order 對象，徹底消除 Young GC */
    private final Deque<Order> orderPool = new ArrayDeque<>(POOL_SIZE);
    private final Trade reusableTrade = new Trade();

    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
        // 預分配物件池
        for (int i = 0; i < 10000; i++) { // 先預分配 1 萬個，不夠時動態建立
            orderPool.add(new Order());
        }
    }

    /** 
      從池中領取物件並重置狀態
     */
    public Order borrowOrder() {
        Order o = orderPool.pollFirst();
        if (o == null) o = new Order();
        // 重置關鍵欄位
        o.setFilled(0); o.setStatus((byte)0); o.setVersion(0); o.setLastSeq(-1);
        return o;
    }

    /** 
      歸還物件至池中
     */
    public void releaseOrder(Order o) {
        if (o != null) orderPool.addLast(o);
    }

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

            final Deque<Order> makers = bestLevelEntry.getValue();
            final long bestPrice = bestLevelEntry.getKey();
            if (isBuy ? (takerPrice < bestPrice) : (takerPrice > bestPrice)) break;

            while (!makers.isEmpty() && taker.getQty() > taker.getFilled()) {
                Order maker = makers.peekFirst();
                if (!orderIndex.containsKey(maker.getOrderId())) {
                    releaseOrder(makers.pollFirst()); // 撤單釋放
                    continue;
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
                    orderIndex.remove(maker.getOrderId());
                    releaseOrder(makers.pollFirst()); // 完全成交釋放
                } else {
                    syncOrder(maker, gwSeq);
                    break;
                }
            }
            if (makers.isEmpty()) counterSide.pollFirstEntry();
            else break;
        }
        if (taker.getQty() > taker.getFilled()) add(taker);
        else releaseOrder(taker); // Taker 直接完全成交，歸還物件
    }

    public void add(Order order) {
        TreeMap<Long, Deque<Order>> levels = (order.getSide() == 0) ? bids : asks;
        levels.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>(INITIAL_DEQUE_CAPACITY)).addLast(order);
        orderIndex.put(order.getOrderId(), order);
    }

    public void syncOrder(Order o, long gwSeq) {
        if (o.getFilled() == o.getQty()) o.setStatus((byte) 2);
        else if (o.getFilled() > 0) o.setStatus((byte) 1);
        o.setVersion(o.getVersion() + 1); o.setLastSeq(gwSeq);
        allOrdersDiskMap.put(o.getOrderId(), o);
        if (o.getStatus() < 2) activeOrderIdDiskMap.put(o.getOrderId(), true);
        else activeOrderIdDiskMap.remove(o.getOrderId());
    }

    public void remove(long orderId) { orderIndex.remove(orderId); }
}
