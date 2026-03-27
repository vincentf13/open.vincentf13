package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.sbe.OrderStatus;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.LongComparator;
import java.util.*;

import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 內存訂單簿 (OrderBook) - 低延遲 Zero-GC 版
 職責：執行價格優先、時間優先 (FIFO) 的撮合邏輯，並維護高效的內存索引。
 */
@Slf4j
public class OrderBook {
    private static final Int2ObjectHashMap<OrderBook> INSTANCES = new Int2ObjectHashMap<>();
    
    // --- 全局對象池 (Object Pools) ---
    private static final Deque<Order> ORDER_POOL = new ArrayDeque<>(200_000);
    private static final Deque<Trade> TRADE_POOL = new ArrayDeque<>(20_000);
    private static final Deque<CidKey> CID_POOL = new ArrayDeque<>(20_000);
    private static final Deque<Deque<Order>> DEQUE_POOL = new ArrayDeque<>(10_000);

    static {
        for (int i = 0; i < 200_000; i++) {
            ORDER_POOL.add(new Order());
            if (i < 20_000) { TRADE_POOL.add(new Trade()); CID_POOL.add(new CidKey()); }
            if (i < 10_000) DEQUE_POOL.add(new ArrayDeque<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY));
        }
    }

    public static CidKey borrowCid() { CidKey c = CID_POOL.pollFirst(); return c != null ? c : new CidKey(); }
    public static void releaseCid(CidKey c) { if (c != null) CID_POOL.addLast(c); }

    public static OrderBook get(int symbolId) {
        return INSTANCES.computeIfAbsent(symbolId, id -> {
            Symbol s = Symbol.of(id);
            if (s == null) throw new IllegalArgumentException("Unknown symbol: " + id);
            return new OrderBook(id, s.getBaseAssetId(), s.getQuoteAssetId());
        });
    }

    public static Collection<OrderBook> getInstances() { return INSTANCES.values(); }
    public static void resetForRecovery() { INSTANCES.clear(); }

    private final int symbolId, baseAssetId, quoteAssetId;
    
    // --- 磁碟持久化映射 ---
    private final ChronicleMap<open.vincentf13.service.spot.infra.chronicle.LongValue, Order> ordersDisk = Storage.self().orders();
    private final ChronicleMap<open.vincentf13.service.spot.infra.chronicle.LongValue, Boolean> activeDisk = Storage.self().activeOrders();
    private final ChronicleMap<open.vincentf13.service.spot.infra.chronicle.LongValue, Trade> tradesDisk = Storage.self().trades();
    
    // --- 批次寫入緩衝 ---
    private final Long2ObjectHashMap<Order> pendingOrders = new Long2ObjectHashMap<>(16384, 0.5f);
    private final Long2ObjectHashMap<Trade> pendingTrades = new Long2ObjectHashMap<>(32768, 0.5f);
    private final LongHashSet pendingAdds = new LongHashSet(8192), pendingRemovals = new LongHashSet(8192);

    // --- 內存撮合數據結構 (Fastutil RB-Tree) ---
    private final Long2ObjectRBTreeMap<Deque<Order>> bids = new Long2ObjectRBTreeMap<>((LongComparator) (k1, k2) -> Long.compare(k2, k1));
    private final Long2ObjectRBTreeMap<Deque<Order>> asks = new Long2ObjectRBTreeMap<>();
    private final Long2ObjectHashMap<Deque<Order>> bidLevels = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY, 0.5f);
    private final Long2ObjectHashMap<Deque<Order>> askLevels = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY, 0.5f);
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_ORDER_COUNT, 0.5f); 
    private final Long2ObjectHashMap<LongHashSet> userOrdersIndex = new Long2ObjectHashMap<>(4096, 0.5f);

    private final open.vincentf13.service.spot.infra.chronicle.LongValue kO = new open.vincentf13.service.spot.infra.chronicle.LongValue();
    private final open.vincentf13.service.spot.infra.chronicle.LongValue kT = new open.vincentf13.service.spot.infra.chronicle.LongValue();
    private final open.vincentf13.service.spot.infra.chronicle.LongValue kA = new open.vincentf13.service.spot.infra.chronicle.LongValue();

    public interface TradeFinalizer {
        void onMatch(Trade trade, Order maker, Order taker, int baseAsset, int quoteAsset);
    }

    private OrderBook(int symbolId, int baseAssetId, int quoteAssetId) {
        this.symbolId = symbolId; this.baseAssetId = baseAssetId; this.quoteAssetId = quoteAssetId;
    }

    /** 執行掛單批次落地 */
    public void flush() {
        if (!pendingOrders.isEmpty()) { 
            pendingOrders.forEach((id, o) -> { kO.set(id); ordersDisk.put(kO, o); if (o.getStatus() >= 2) releaseOrder(o); });
            pendingOrders.clear(); 
        }
        if (!pendingTrades.isEmpty()) { 
            pendingTrades.forEach((id, t) -> { kT.set(id); tradesDisk.put(kT, t); releaseTrade(t); });
            pendingTrades.clear(); 
        }
        if (!pendingAdds.isEmpty()) { pendingAdds.forEach(id -> { kA.set(id); activeDisk.put(kA, Boolean.TRUE); }); pendingAdds.clear(); }
        if (!pendingRemovals.isEmpty()) { pendingRemovals.forEach(id -> { kA.set(id); activeDisk.remove(kA); }); pendingRemovals.clear(); }
    }

    public void recoverOrder(Order o) {
        if (o == null || o.isTerminal()) return;
        Order r = borrowOrder();
        r.copyFrom(o);
        add(r);
    }

    private Order borrowOrder() { Order o = ORDER_POOL.pollFirst(); return o != null ? o : new Order(); }
    public void releaseOrder(Order o) { if (o != null) ORDER_POOL.addLast(o); }
    private Trade borrowTrade() { Trade t = TRADE_POOL.pollFirst(); return t != null ? t : new Trade(); }
    private void releaseTrade(Trade t) { if (t != null) TRADE_POOL.addLast(t); }

    /** 下單入口 */
    public Order handleCreate(long orderId, long userId, int symbolId, long price, long qty, Side side, long clientOrderId, long timestamp, long gwSeq,
                             long frozenAmount,
                             open.vincentf13.service.spot.model.WalProgress progress, TradeFinalizer finalizer) {
        Order taker = borrowOrder();
        taker.fill(orderId, userId, symbolId, price, qty, (byte)(side == Side.BUY ? OrderSide.BUY : OrderSide.SELL), clientOrderId, timestamp, gwSeq, frozenAmount);
        
        match(taker, gwSeq, timestamp, progress, finalizer);
        syncOrder(taker, gwSeq);
        return taker;
    }

    private void match(Order taker, long gwSeq, long timestamp, open.vincentf13.service.spot.model.WalProgress progress, TradeFinalizer finalizer) {
        final boolean isBuy = taker.getSide() == OrderSide.BUY;
        final Long2ObjectRBTreeMap<Deque<Order>> counters = isBuy ? asks : bids;
        final long takerPrice = taker.getPrice();

        while (taker.remainingQty() > 0 && !counters.isEmpty()) {
            final long bestPrice = counters.firstLongKey();
            if (isBuy ? (takerPrice < bestPrice) : (takerPrice > bestPrice)) break;

            executeMatchAtLevel(taker, bestPrice, counters, getLevels(!isBuy), gwSeq, timestamp, progress, finalizer);
        }
        
        if (taker.remainingQty() > 0) add(taker);
        else taker.setStatus((byte) OrderStatus.FILLED.ordinal());
    }

    private void executeMatchAtLevel(Order taker, long price, Long2ObjectRBTreeMap<Deque<Order>> counters,
                                   Long2ObjectHashMap<Deque<Order>> levels, long gwSeq, long timestamp,
                                   open.vincentf13.service.spot.model.WalProgress progress, TradeFinalizer finalizer) {
        final Deque<Order> makers = levels.get(price);
        if (makers == null || makers.isEmpty()) {
            cleanupEmptyLevel(price, counters, levels, makers);
            return;
        }

        while (!makers.isEmpty() && taker.remainingQty() > 0) {
            Order maker = makers.peekFirst();
            if (!orderIndex.containsKey(maker.getOrderId())) { makers.pollFirst(); continue; } // 已被撤單

            long matchQty = Math.min(taker.remainingQty(), maker.remainingQty());
            long tid = progress.getAndIncrTradeId();
            
            Trade t = borrowTrade();
            t.setTradeId(tid); t.setOrderId(maker.getOrderId()); t.setPrice(price); t.setQty(matchQty); t.setTime(timestamp); t.setLastSeq(gwSeq);
            pendingTrades.put(tid, t);

            maker.setFilled(maker.getFilled() + matchQty);
            taker.setFilled(taker.getFilled() + matchQty);

            StaticMetricsHolder.addCounter(MetricsKey.MATCH_COUNT, 1);
            finalizer.onMatch(t, maker, taker, baseAssetId, quoteAssetId);
            if (maker.remainingQty() == 0) { finalizeOrder(maker, gwSeq); makers.pollFirst(); } 
            else { syncOrder(maker, gwSeq); break; }
        }
        
        if (makers.isEmpty()) cleanupEmptyLevel(price, counters, levels, makers);
    }

    /** 訂單結束處理 (成交或撤單) */
    private void finalizeOrder(Order o, long gwSeq) {
        syncOrder(o, gwSeq);
        orderIndex.remove(o.getOrderId());
        LongHashSet set = userOrdersIndex.get(o.getUserId());
        if (set != null) {
            set.remove(o.getOrderId());
            if (set.isEmpty()) userOrdersIndex.remove(o.getUserId());
        }
    }

    private void cleanupEmptyLevel(long price, Long2ObjectRBTreeMap<Deque<Order>> counters,
                                   Long2ObjectHashMap<Deque<Order>> levels, Deque<Order> makers) {
        counters.remove(price);
        Deque<Order> removed = levels.remove(price);
        if (removed != null) {
            removed.clear();
            DEQUE_POOL.addLast(removed);
        } else if (makers != null) {
            makers.clear();
            DEQUE_POOL.addLast(makers);
        }
    }

    private void add(Order order) {
        final long price = order.getPrice();
        Long2ObjectHashMap<Deque<Order>> levels = getLevels(order.getSide() == OrderSide.BUY);
        Deque<Order> level = levels.get(price);
        if (level == null) {
            level = DEQUE_POOL.pollFirst();
            if (level == null) level = new ArrayDeque<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY);
            else level.clear();
            getTree(order.getSide() == OrderSide.BUY).put(price, level);
            levels.put(price, level);
        }
        level.addLast(order);
        orderIndex.put(order.getOrderId(), order);
        userOrdersIndex.computeIfAbsent(order.getUserId(), k -> new LongHashSet(16)).add(order.getOrderId());
    }

    public void syncOrder(Order o, long gwSeq) {
        if (o.getStatus() != OrderStatus.CANCELED.ordinal()) {
            o.setStatus((byte) (o.remainingQty() == 0
                ? OrderStatus.FILLED.ordinal()
                : (o.getFilled() > 0 ? OrderStatus.PARTIALLY_FILLED.ordinal() : OrderStatus.NEW.ordinal())));
        }
        o.setVersion(o.getVersion() + 1);
        o.setLastSeq(gwSeq);
        pendingOrders.put(o.getOrderId(), o);

        if (!o.isTerminal()) { pendingAdds.add(o.getOrderId()); pendingRemovals.remove(o.getOrderId()); } 
        else { pendingRemovals.add(o.getOrderId()); pendingAdds.remove(o.getOrderId()); }
    }

    public Order cancel(long orderId, long userId, long gwSeq) {
        Order o = orderIndex.get(orderId);
        if (o == null || o.getUserId() != userId || o.isTerminal()) return null;

        Long2ObjectHashMap<Deque<Order>> levels = getLevels(o.getSide() == OrderSide.BUY);
        Deque<Order> level = levels.get(o.getPrice());
        if (level != null) {
            level.remove(o);
            if (level.isEmpty()) cleanupEmptyLevel(o.getPrice(), getTree(o.getSide() == OrderSide.BUY), levels, level);
        }
        
        o.setStatus((byte) OrderStatus.CANCELED.ordinal());
        finalizeOrder(o, gwSeq);
        return o;
    }

    public int getBaseAssetId() { return baseAssetId; }
    public int getQuoteAssetId() { return quoteAssetId; }
    public boolean hasOrder(long orderId) { return orderIndex.containsKey(orderId); }
    public int activeOrderCount() { return orderIndex.size(); }

    private Long2ObjectRBTreeMap<Deque<Order>> getTree(boolean buySide) {
        return buySide ? bids : asks;
    }

    private Long2ObjectHashMap<Deque<Order>> getLevels(boolean buySide) {
        return buySide ? bidLevels : askLevels;
    }

    public void validateState() {
        validateLevels(bidLevels, OrderSide.BUY);
        validateLevels(askLevels, OrderSide.SELL);

        final int[] indexedCount = new int[1];
        orderIndex.forEach((orderId, order) -> {
            indexedCount[0]++;
            if (order.isTerminal()) {
                throw new IllegalStateException("Terminal order remains indexed, orderId=" + orderId);
            }
            Deque<Order> level = getLevels(order.getSide() == OrderSide.BUY).get(order.getPrice());
            if (level == null || !level.contains(order)) {
                throw new IllegalStateException("Indexed order missing from price level, orderId=" + orderId);
            }
            LongHashSet userOrders = userOrdersIndex.get(order.getUserId());
            if (userOrders == null || !userOrders.contains(orderId)) {
                throw new IllegalStateException("Indexed order missing from user index, orderId=" + orderId);
            }
        });

        if (indexedCount[0] != orderIndex.size()) {
            throw new IllegalStateException("Order index iteration mismatch");
        }
    }

    private void validateLevels(Long2ObjectHashMap<Deque<Order>> levels, byte expectedSide) {
        levels.forEach((price, queue) -> {
            if (queue == null || queue.isEmpty()) {
                throw new IllegalStateException("Empty price level retained, side=" + expectedSide + ", price=" + price);
            }

            for (Order order : queue) {
                if (order.getSide() != expectedSide) {
                    throw new IllegalStateException("Order side mismatch, orderId=" + order.getOrderId());
                }
                if (order.getPrice() != price) {
                    throw new IllegalStateException("Order price mismatch, orderId=" + order.getOrderId());
                }
                if (order.isTerminal()) {
                    throw new IllegalStateException("Terminal order present in price level, orderId=" + order.getOrderId());
                }
            }
        });
    }

    public static void rebuildActiveOrdersIndexes() {
        resetForRecovery();
        ChronicleMap<open.vincentf13.service.spot.infra.chronicle.LongValue, Order> all = Storage.self().orders();
        Storage.self().activeOrders().forEach((id, active) -> {
            Order o = all.getUsing(id, new Order());
            if (o != null && !o.isTerminal()) OrderBook.get(o.getSymbolId()).recoverOrder(o);
        });
    }
}
