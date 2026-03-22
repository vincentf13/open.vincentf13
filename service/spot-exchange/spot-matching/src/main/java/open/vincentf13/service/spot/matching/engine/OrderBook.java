package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

import java.util.concurrent.atomic.AtomicLong;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 內存訂單簿 (OrderBook) - 極致性能優化版
 */
@Slf4j
public class OrderBook {
    public static final AtomicLong TOTAL_MATCH_COUNT = new AtomicLong(0);
    private static final Int2ObjectHashMap<OrderBook> INSTANCES = new Int2ObjectHashMap<>();
    
    private static final Deque<Order> GLOBAL_ORDER_POOL = new ConcurrentLinkedDeque<>();
    private static final Deque<Deque<Order>> GLOBAL_DEQUE_POOL = new ConcurrentLinkedDeque<>();

    static {
        for (int i = 0; i < MatchingConfig.INITIAL_POOL_SIZE; i++) GLOBAL_ORDER_POOL.add(new Order());
        for (int i = 0; i < 10000; i++) GLOBAL_DEQUE_POOL.add(new ArrayDeque<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY));
    }

    public static OrderBook get(int symbolId) {
        return INSTANCES.computeIfAbsent(symbolId, id -> {
            Symbol s = Symbol.of(id);
            if (s == null) throw new IllegalArgumentException("未知的交易對 ID: " + id);
            return new OrderBook(id, s.getBaseAssetId(), s.getQuoteAssetId());
        });
    }

    public static Collection<OrderBook> getInstances() {
        return INSTANCES.values();
    }

    private final int symbolId;
    private final int baseAssetId;
    private final int quoteAssetId;
    
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();
    private final ChronicleMap<Long, Boolean> activeOrderIdDiskMap = Storage.self().activeOrders();
    private final ChronicleMap<Long, byte[]> userActiveOrdersDiskMap = Storage.self().userActiveOrders();
    private final ChronicleMap<Long, Trade> tradeHistoryDiskMap = Storage.self().trades();
    
    private final Long2ObjectHashMap<Order> pendingOrders = new Long2ObjectHashMap<>();
    private final Long2ObjectHashMap<Trade> pendingTrades = new Long2ObjectHashMap<>();
    private final LongHashSet pendingActiveRemovals = new LongHashSet();
    private final LongHashSet pendingActiveAdds = new LongHashSet();
    private final LongHashSet pendingUserActiveChanged = new LongHashSet();

    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();
    private final Long2ObjectHashMap<Deque<Order>> priceLevelIndex = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY, 0.5f);
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_ORDER_COUNT, 0.5f); 
    private final Long2ObjectHashMap<LongHashSet> userOrderIdsIndex = new Long2ObjectHashMap<>(4096, 0.5f);

    private static final Order SCAN_REUSABLE = new Order();

    public interface TradeFinalizer {
        void onMatch(long tradeId, Order maker, long price, long qty, int baseAsset, int quoteAsset);
    }

    private OrderBook(int symbolId, int baseAssetId, int quoteAssetId) {
        this.symbolId = symbolId;
        this.baseAssetId = baseAssetId;
        this.quoteAssetId = quoteAssetId;
    }

    public void flush() {
        if (!pendingOrders.isEmpty()) { pendingOrders.forEach(allOrdersDiskMap::put); pendingOrders.clear(); }
        if (!pendingTrades.isEmpty()) { pendingTrades.forEach(tradeHistoryDiskMap::put); pendingTrades.clear(); }
        if (!pendingActiveAdds.isEmpty()) { pendingActiveAdds.forEach(id -> activeOrderIdDiskMap.put(id, Boolean.TRUE)); pendingActiveAdds.clear(); }
        if (!pendingActiveRemovals.isEmpty()) { pendingActiveRemovals.forEach(activeOrderIdDiskMap::remove); pendingActiveRemovals.clear(); }
        
        if (!pendingUserActiveChanged.isEmpty()) {
            pendingUserActiveChanged.forEach(this::syncUserActiveToDisk);
            pendingUserActiveChanged.clear();
        }
    }

    private void syncUserActiveToDisk(long userId) {
        LongHashSet activeIds = userOrderIdsIndex.get(userId);
        if (activeIds == null || activeIds.isEmpty()) {
            userActiveOrdersDiskMap.remove(userId);
        } else {
            byte[] bytes = new byte[activeIds.size() * 8];
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes);
            activeIds.forEach(buf::putLong);
            userActiveOrdersDiskMap.put(userId, bytes);
        }
    }

    public void recoverOrder(Order o) {
        if (o == null || o.getStatus() >= 2) return;
        Order recovered = borrowOrder();
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

    private Order borrowOrder() {
        Order o = GLOBAL_ORDER_POOL.pollFirst();
        return (o == null) ? new Order() : o;
    }

    public void releaseOrder(Order o) { if (o != null) GLOBAL_ORDER_POOL.addLast(o); }

    public Order handleCreate(long orderId, long userId, int symbolId, long price, long qty, Side side, long clientOrderId, long timestamp, long gwSeq,
                             Supplier<Long> tradeIdSupplier, TradeFinalizer finalizer) {
        Order taker = borrowAndFill(orderId, userId, symbolId, price, qty, side, clientOrderId, gwSeq);
        match(taker, gwSeq, timestamp, tradeIdSupplier, finalizer);
        syncOrder(taker, gwSeq);
        return taker;
    }

    private Order borrowAndFill(long orderId, long userId, int symbolId, long price, long qty, Side side, long clientOrderId, long gwSeq) {
        Order o = borrowOrder();
        o.setOrderId(orderId); o.setUserId(userId); o.setSymbolId(symbolId);
        o.setPrice(price); o.setQty(qty); o.setFilled(0);
        o.setSide((byte)(side == Side.BUY ? OrderSide.BUY : OrderSide.SELL));
        o.setStatus((byte)0); o.setVersion(1); o.setLastSeq(gwSeq);
        o.setClientOrderId(clientOrderId);
        return o;
    }

    private void match(Order taker, long gwSeq, long timestamp, Supplier<Long> tradeIdSupplier, TradeFinalizer finalizer) {
        final boolean isBuy = taker.getSide() == OrderSide.BUY;
        final TreeMap<Long, Deque<Order>> counterSide = isBuy ? asks : bids;
        final long takerPrice = taker.getPrice();

        while (taker.getQty() > taker.getFilled()) {
            if (counterSide.isEmpty()) break;
            final long bestPrice = counterSide.firstKey();
            if (isBuy ? (takerPrice < bestPrice) : (takerPrice > bestPrice)) break;

            final Deque<Order> makers = counterSide.get(bestPrice);
            while (makers != null && !makers.isEmpty() && taker.getQty() > taker.getFilled()) {
                Order maker = makers.peekFirst();
                if (!orderIndex.containsKey(maker.getOrderId())) {
                    releaseOrder(makers.pollFirst()); continue;
                }

                long matchQty = Math.min(taker.getQty() - taker.getFilled(), maker.getQty() - maker.getFilled());
                long tid = tradeIdSupplier.get();
                
                Trade t = new Trade();
                t.setTradeId(tid); t.setOrderId(maker.getOrderId());
                t.setPrice(bestPrice); t.setQty(matchQty);
                t.setTime(timestamp); t.setLastSeq(gwSeq);
                pendingTrades.put(tid, t);

                maker.setFilled(maker.getFilled() + matchQty);
                taker.setFilled(taker.getFilled() + matchQty);
                
                TOTAL_MATCH_COUNT.incrementAndGet();
                finalizer.onMatch(tid, maker, bestPrice, matchQty, baseAssetId, quoteAssetId);

                if (maker.getFilled() == maker.getQty()) {
                    syncOrder(maker, gwSeq);
                    orderIndex.remove(maker.getOrderId());
                    LongHashSet set = userOrderIdsIndex.get(maker.getUserId());
                    if (set != null) set.remove(maker.getOrderId());
                    releaseOrder(makers.pollFirst()); 
                } else {
                    syncOrder(maker, gwSeq);
                    break;
                }
            }
            if (makers != null && makers.isEmpty()) {
                counterSide.remove(bestPrice);
                priceLevelIndex.remove(bestPrice);
                GLOBAL_DEQUE_POOL.addLast(makers); 
            } else break;
        }
        if (taker.getQty() > taker.getFilled()) add(taker);
    }

    private void add(Order order) {
        final long price = order.getPrice();
        Deque<Order> level = priceLevelIndex.get(price);
        if (level == null) {
            TreeMap<Long, Deque<Order>> levels = (order.getSide() == OrderSide.BUY) ? bids : asks;
            level = GLOBAL_DEQUE_POOL.pollFirst();
            if (level == null) level = new ArrayDeque<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY);
            levels.put(price, level);
            priceLevelIndex.put(price, level);
        }
        level.addLast(order);
        orderIndex.put(order.getOrderId(), order);
        userOrderIdsIndex.computeIfAbsent(order.getUserId(), k -> new LongHashSet(16)).add(order.getOrderId());
    }

    public void syncOrder(Order o, long gwSeq) {
        if (o.getFilled() == o.getQty()) o.setStatus((byte) 2);
        else if (o.getFilled() > 0) o.setStatus((byte) 1);
        o.setVersion(o.getVersion() + 1); o.setLastSeq(gwSeq);
        
        pendingOrders.put(o.getOrderId(), o);
        final long uid = o.getUserId();
        final long oid = o.getOrderId();
        
        if (o.getStatus() < 2) {
            pendingActiveAdds.add(oid);
            pendingActiveRemovals.remove(oid);
            userOrderIdsIndex.computeIfAbsent(uid, k -> new LongHashSet(16)).add(oid);
        } else {
            pendingActiveRemovals.add(oid);
            pendingActiveAdds.remove(oid);
            LongHashSet set = userOrderIdsIndex.get(uid);
            if (set != null) set.remove(oid);
        }
        pendingUserActiveChanged.add(uid);
    }

    public void remove(long orderId) { 
        Order o = orderIndex.remove(orderId); 
        if (o != null) {
            Deque<Order> level = priceLevelIndex.get(o.getPrice());
            if (level != null) {
                level.remove(o);
                if (level.isEmpty()) {
                    priceLevelIndex.remove(o.getPrice());
                    if (o.getSide() == OrderSide.BUY) bids.remove(o.getPrice());
                    else asks.remove(o.getPrice());
                    GLOBAL_DEQUE_POOL.addLast(level);
                }
            }
            pendingActiveRemovals.add(orderId);
            pendingUserActiveChanged.add(o.getUserId());
            LongHashSet set = userOrderIdsIndex.get(o.getUserId());
            if (set != null) set.remove(orderId);
            releaseOrder(o);
        }
    }

    public int getBaseAssetId() { return baseAssetId; }
    public int getQuoteAssetId() { return quoteAssetId; }

    public static void rebuildActiveOrdersIndexes() {
        ChronicleMap<Long, Order> allOrders = Storage.self().orders();
        Storage.self().activeOrders().forEach((id, active) -> {
            Order o = allOrders.getUsing(id, SCAN_REUSABLE);
            if (o != null && o.getStatus() < 2) OrderBook.get(o.getSymbolId()).recoverOrder(o);
        });
    }
}
