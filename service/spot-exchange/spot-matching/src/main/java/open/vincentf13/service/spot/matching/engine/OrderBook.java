package open.vincentf13.service.spot.matching.engine;

import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import java.util.*;
import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 內存訂單簿 (OrderBook)
 職責：領域狀態機，自主管理訂單生命週期、撮合流與數據持久化
 */
public class OrderBook {
    private static final Int2ObjectHashMap<OrderBook> INSTANCES = new Int2ObjectHashMap<>();
    
    public static OrderBook get(int symbolId) {
        return INSTANCES.computeIfAbsent(symbolId, OrderBook::new);
    }

    private static final int INITIAL_DEQUE_CAPACITY = 4096;
    private static final int POOL_MAX_SIZE = 100_000;

    private final int symbolId;
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();
    private final ChronicleMap<Long, Boolean> activeOrderIdDiskMap = Storage.self().activeOrders();
    private final ChronicleMap<Long, Trade> tradeHistoryDiskMap = Storage.self().trades();

    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(200_000, 0.5f); 

    private final Deque<Order> orderPool = new ArrayDeque<>(POOL_MAX_SIZE);
    private final Trade reusableTrade = new Trade();

    @FunctionalInterface public interface TradeFinalizer {
        void onMatch(Order maker, long price, long qty);
    }

    private OrderBook(int symbolId) {
        this.symbolId = symbolId;
        for (int i = 0; i < 1000; i++) orderPool.add(new Order());
    }

    /** 
      指令入口：封裝 Admission -> 撮合 -> 狀態落地 
      接收 cidStr 以避免重複分配
     */
    public Order handleCreate(long orderId, OrderCreateDecoder sbe, String cidStr, long gwSeq, 
                             Supplier<Long> tradeIdSupplier, TradeFinalizer finalizer) {
        Order taker = borrowAndFill(orderId, sbe, cidStr, gwSeq);
        match(taker, gwSeq, sbe.timestamp(), tradeIdSupplier, finalizer);
        syncOrder(taker, gwSeq);
        return taker;
    }

    public void recoverOrder(Order o) {
        if (o == null || o.getStatus() >= 2) return;
        Order recovered = new Order();
        copyOrder(o, recovered);
        add(recovered);
    }

    private void copyOrder(Order src, Order dst) {
        dst.setOrderId(src.getOrderId()); dst.setUserId(src.getUserId());
        dst.setSymbolId(src.getSymbolId()); dst.setPrice(src.getPrice());
        dst.setQty(src.getQty()); dst.setFilled(src.getFilled());
        dst.setSide(src.getSide()); dst.setStatus(src.getStatus());
        dst.setVersion(src.getVersion()); dst.setLastSeq(src.getLastSeq());
        dst.setClientOrderId(src.getClientOrderId());
    }

    private Order borrowAndFill(long orderId, OrderCreateDecoder sbe, String cidStr, long gwSeq) {
        Order o = orderPool.pollFirst();
        if (o == null) o = new Order();
        o.setOrderId(orderId); o.setUserId(sbe.userId()); o.setSymbolId(sbe.symbolId());
        o.setPrice(sbe.price()); o.setQty(sbe.qty()); o.setFilled(0);
        o.setSide((byte)(sbe.side() == Side.BUY ? 0 : 1)); o.setStatus((byte)0);
        o.setVersion(1); o.setLastSeq(gwSeq);
        o.setClientOrderId(cidStr);
        return o;
    }

    public void releaseOrder(Order o) { if (o != null) orderPool.addLast(o); }

    /** 
      全局啟動恢復：優化為單個復用對象掃描，實現啟動期 Zero-GC
     */
    public static void rebuildAll() {
        ChronicleMap<Long, Order> allOrders = Storage.self().orders();
        Order scanReusable = new Order();
        Storage.self().activeOrders().forEach((id, active) -> {
            Order o = allOrders.getUsing(id, scanReusable);
            if (o != null && o.getStatus() < 2) OrderBook.get(o.getSymbolId()).recoverOrder(o);
        });
    }

    private void match(Order taker, long gwSeq, long timestamp, Supplier<Long> tradeIdSupplier, TradeFinalizer finalizer) {
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
                    releaseOrder(makers.pollFirst()); continue;
                }

                long matchQty = Math.min(taker.getQty() - taker.getFilled(), maker.getQty() - maker.getFilled());
                long tid = tradeIdSupplier.get();
                
                reusableTrade.setTradeId(tid); reusableTrade.setOrderId(maker.getOrderId());
                reusableTrade.setPrice(bestPrice); reusableTrade.setQty(matchQty);
                reusableTrade.setTime(timestamp); reusableTrade.setLastSeq(gwSeq);
                tradeHistoryDiskMap.put(tid, reusableTrade);

                maker.setFilled(maker.getFilled() + matchQty);
                taker.setFilled(taker.getFilled() + matchQty);
                finalizer.onMatch(maker, bestPrice, matchQty);

                if (maker.getFilled() == maker.getQty()) {
                    syncOrder(maker, gwSeq);
                    orderIndex.remove(maker.getOrderId());
                    releaseOrder(makers.pollFirst()); 
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
