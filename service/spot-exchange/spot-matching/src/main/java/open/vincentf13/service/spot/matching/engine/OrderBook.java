package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.LongValue;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.sbe.OrderStatus;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.LongComparator;

import java.util.Collection;
import java.util.Deque;

import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 內存訂單簿 (OrderBook)
 *
 * 職責：價格優先-時間優先 (FIFO) 撮合邏輯 + 內存索引維護。
 * 物件池委派給 {@link MatchingPool}，持久化透過批次緩衝 + flush()。
 */
@Slf4j
public class OrderBook {

    // ========== 靜態工廠 ==========

    private static final Int2ObjectHashMap<OrderBook> INSTANCES = new Int2ObjectHashMap<>();

    public static OrderBook get(int symbolId) {
        return INSTANCES.computeIfAbsent(symbolId, id -> {
            Symbol s = Symbol.of(id);
            if (s == null) throw new IllegalArgumentException("Unknown symbol: " + id);
            return new OrderBook(id, s.getBaseAssetId(), s.getQuoteAssetId());
        });
    }

    public static Collection<OrderBook> getInstances() { return INSTANCES.values(); }
    public static void resetForRecovery() { INSTANCES.clear(); }

    // ========== 回呼介面 ==========

    public interface TradeFinalizer {
        void onMatch(Trade trade, Order maker, Order taker, int baseAsset, int quoteAsset);
    }

    // ========== 實例狀態 ==========

    private final int symbolId, baseAssetId, quoteAssetId;

    // 磁碟映射
    private final ChronicleMap<LongValue, Order> ordersDisk = Storage.self().orders();
    private final ChronicleMap<LongValue, Boolean> activeDisk = Storage.self().activeOrders();
    private final ChronicleMap<LongValue, Trade> tradesDisk = Storage.self().trades();

    // 批次寫入緩衝
    private final Long2ObjectHashMap<Order> pendingOrders = new Long2ObjectHashMap<>(16384, 0.5f);
    private final Long2ObjectHashMap<Trade> pendingTrades = new Long2ObjectHashMap<>(32768, 0.5f);
    private final LongHashSet pendingAdds = new LongHashSet(8192), pendingRemovals = new LongHashSet(8192);

    // 撮合結構
    private final Long2ObjectRBTreeMap<Deque<Order>> bids = new Long2ObjectRBTreeMap<>((LongComparator) (k1, k2) -> Long.compare(k2, k1));
    private final Long2ObjectRBTreeMap<Deque<Order>> asks = new Long2ObjectRBTreeMap<>();
    private final Long2ObjectHashMap<Deque<Order>> bidLevels = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY, 0.5f);
    private final Long2ObjectHashMap<Deque<Order>> askLevels = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY, 0.5f);
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_ORDER_COUNT, 0.5f);
    private final Long2ObjectHashMap<LongHashSet> userOrdersIndex = new Long2ObjectHashMap<>(4096, 0.5f);

    // Flush 用可重用 Key
    private final LongValue kO = new LongValue();
    private final LongValue kT = new LongValue();
    private final LongValue kA = new LongValue();

    private OrderBook(int symbolId, int baseAssetId, int quoteAssetId) {
        this.symbolId = symbolId;
        this.baseAssetId = baseAssetId;
        this.quoteAssetId = quoteAssetId;
    }

    // ========== 撮合核心 ==========

    /** 下單入口：借取池物件 → 撮合 → 同步狀態 */
    public Order handleCreate(long orderId, long userId, int symbolId, long price, long qty, Side side, long clientOrderId,
                              long timestamp, long gwSeq, long frozenAmount,
                              open.vincentf13.service.spot.model.WalProgress progress, TradeFinalizer finalizer) {
        Order taker = MatchingPool.borrowOrder();
        taker.fill(orderId, userId, symbolId, price, qty, (byte)(side == Side.BUY ? OrderSide.BUY : OrderSide.SELL),
                   clientOrderId, timestamp, gwSeq, frozenAmount);
        match(taker, gwSeq, timestamp, progress, finalizer);
        syncOrder(taker, gwSeq);
        return taker;
    }

    /** 撤單：移出 book → 標記 CANCELED → 同步 */
    public Order cancel(long orderId, long userId, long gwSeq) {
        Order o = orderIndex.get(orderId);
        if (o == null || o.getUserId() != userId || o.isTerminal()) return null;

        removeFromLevel(o);
        o.setStatus((byte) OrderStatus.CANCELED.value());
        finalizeOrder(o, gwSeq);
        return o;
    }

    /** 冷啟動恢復：從磁碟讀取的活躍訂單重新加入 book */
    public void recoverOrder(Order o) {
        if (o == null || o.isTerminal()) return;
        o.validateState();
        Order r = MatchingPool.borrowOrder();
        r.copyFrom(o);
        addToBook(r);
    }

    // ========== 內部撮合邏輯 ==========

    private void match(Order taker, long gwSeq, long timestamp,
                       open.vincentf13.service.spot.model.WalProgress progress, TradeFinalizer finalizer) {
        boolean isBuy = taker.getSide() == OrderSide.BUY;
        Long2ObjectRBTreeMap<Deque<Order>> counters = isBuy ? asks : bids;

        while (taker.remainingQty() > 0 && !counters.isEmpty()) {
            long bestPrice = counters.firstLongKey();
            if (isBuy ? (taker.getPrice() < bestPrice) : (taker.getPrice() > bestPrice)) break;
            executeMatchAtLevel(taker, bestPrice, counters, getLevels(!isBuy), gwSeq, timestamp, progress, finalizer);
        }

        if (taker.remainingQty() > 0) addToBook(taker);
        else taker.setStatus((byte) OrderStatus.FILLED.value());
    }

    private void executeMatchAtLevel(Order taker, long price, Long2ObjectRBTreeMap<Deque<Order>> counters,
                                     Long2ObjectHashMap<Deque<Order>> levels, long gwSeq, long timestamp,
                                     open.vincentf13.service.spot.model.WalProgress progress, TradeFinalizer finalizer) {
        Deque<Order> makers = levels.get(price);
        if (makers == null || makers.isEmpty()) { cleanupEmptyLevel(price, counters, levels, makers); return; }

        while (!makers.isEmpty() && taker.remainingQty() > 0) {
            Order maker = makers.peekFirst();
            if (!orderIndex.containsKey(maker.getOrderId())) { makers.pollFirst(); continue; }
            if (maker.getUserId() == taker.getUserId()) { makers.pollFirst(); continue; } // 防止自成交

            long matchQty = Math.min(taker.remainingQty(), maker.remainingQty());
            Trade t = MatchingPool.borrowTrade();
            t.setTradeId(progress.nextTradeId()); t.setOrderId(maker.getOrderId());
            t.setPrice(price); t.setQty(matchQty); t.setTime(timestamp); t.setLastSeq(gwSeq);
            pendingTrades.put(t.getTradeId(), t);

            maker.setFilled(maker.getFilled() + matchQty);
            taker.setFilled(taker.getFilled() + matchQty);
            StaticMetricsHolder.addCounter(MetricsKey.MATCH_COUNT, 1);
            finalizer.onMatch(t, maker, taker, baseAssetId, quoteAssetId);

            if (maker.remainingQty() == 0) { finalizeOrder(maker, gwSeq); makers.pollFirst(); }
            else { syncOrder(maker, gwSeq); break; }
        }
        if (makers.isEmpty()) cleanupEmptyLevel(price, counters, levels, makers);
    }

    // ========== 索引與狀態管理 ==========

    private void addToBook(Order order) {
        long price = order.getPrice();
        Long2ObjectHashMap<Deque<Order>> levels = getLevels(order.getSide() == OrderSide.BUY);
        Deque<Order> level = levels.get(price);
        if (level == null) {
            level = MatchingPool.borrowDeque();
            getTree(order.getSide() == OrderSide.BUY).put(price, level);
            levels.put(price, level);
        }
        level.addLast(order);
        orderIndex.put(order.getOrderId(), order);
        userOrdersIndex.computeIfAbsent(order.getUserId(), k -> new LongHashSet(16)).add(order.getOrderId());
    }

    private void removeFromLevel(Order o) {
        Long2ObjectHashMap<Deque<Order>> levels = getLevels(o.getSide() == OrderSide.BUY);
        Deque<Order> level = levels.get(o.getPrice());
        if (level != null) {
            level.remove(o);
            if (level.isEmpty()) cleanupEmptyLevel(o.getPrice(), getTree(o.getSide() == OrderSide.BUY), levels, level);
        }
    }

    private void finalizeOrder(Order o, long gwSeq) {
        syncOrder(o, gwSeq);
        orderIndex.remove(o.getOrderId());
        LongHashSet set = userOrdersIndex.get(o.getUserId());
        if (set != null) { set.remove(o.getOrderId()); if (set.isEmpty()) userOrdersIndex.remove(o.getUserId()); }
    }

    private void syncOrder(Order o, long gwSeq) {
        if (o.getStatus() != OrderStatus.CANCELED.value()) {
            o.setStatus((byte) (o.remainingQty() == 0
                    ? OrderStatus.FILLED.value()
                    : (o.getFilled() > 0 ? OrderStatus.PARTIALLY_FILLED.value() : OrderStatus.NEW.value())));
        }
        o.setVersion(o.getVersion() + 1);
        o.setLastSeq(gwSeq);
        if (o.getStatus() != OrderStatus.CANCELED.value()) o.validateState();
        pendingOrders.put(o.getOrderId(), o);
        if (!o.isTerminal()) { pendingAdds.add(o.getOrderId()); pendingRemovals.remove(o.getOrderId()); }
        else { pendingRemovals.add(o.getOrderId()); pendingAdds.remove(o.getOrderId()); }
    }

    private void cleanupEmptyLevel(long price, Long2ObjectRBTreeMap<Deque<Order>> counters,
                                   Long2ObjectHashMap<Deque<Order>> levels, Deque<Order> deque) {
        counters.remove(price);
        Deque<Order> removed = levels.remove(price);
        MatchingPool.releaseDeque(removed != null ? removed : deque);
    }

    private Long2ObjectRBTreeMap<Deque<Order>> getTree(boolean buy) { return buy ? bids : asks; }
    private Long2ObjectHashMap<Deque<Order>> getLevels(boolean buy) { return buy ? bidLevels : askLevels; }

    // ========== 持久化 ==========

    public void flush() {
        if (!pendingOrders.isEmpty()) {
            pendingOrders.forEach((id, o) -> {
                kO.set(id);
                ordersDisk.put(kO, o);
                if (o.isTerminal()) MatchingPool.releaseOrder(o);
            });
            pendingOrders.clear();
        }
        if (!pendingTrades.isEmpty()) {
            pendingTrades.forEach((id, t) -> {
                kT.set(id);
                tradesDisk.put(kT, t);
                MatchingPool.releaseTrade(t);
            });
            pendingTrades.clear();
        }
        if (!pendingAdds.isEmpty()) {
            pendingAdds.forEach(id -> { kA.set(id); activeDisk.put(kA, Boolean.TRUE); });
            pendingAdds.clear();
        }
        if (!pendingRemovals.isEmpty()) {
            pendingRemovals.forEach(id -> { kA.set(id); activeDisk.remove(kA); });
            pendingRemovals.clear();
        }
    }

    // ========== Accessors ==========

    public int getBaseAssetId() { return baseAssetId; }
    public int getQuoteAssetId() { return quoteAssetId; }

    // ========== 狀態驗證 (冷啟動 / 測試用) ==========

    public void validateState() {
        validateLevels(bidLevels, OrderSide.BUY);
        validateLevels(askLevels, OrderSide.SELL);

        final int[] indexedCount = new int[1];
        orderIndex.forEach((orderId, order) -> {
            indexedCount[0]++;
            if (order.isTerminal()) throw new IllegalStateException("Terminal order indexed, orderId=" + orderId);
            Deque<Order> level = getLevels(order.getSide() == OrderSide.BUY).get(order.getPrice());
            if (level == null || !level.contains(order)) throw new IllegalStateException("Order missing from level, orderId=" + orderId);
            LongHashSet userOrders = userOrdersIndex.get(order.getUserId());
            if (userOrders == null || !userOrders.contains(orderId)) throw new IllegalStateException("Order missing from user index, orderId=" + orderId);
        });
        if (indexedCount[0] != orderIndex.size()) throw new IllegalStateException("Order index iteration mismatch");
    }

    private void validateLevels(Long2ObjectHashMap<Deque<Order>> levels, byte expectedSide) {
        levels.forEach((price, queue) -> {
            if (queue == null || queue.isEmpty()) throw new IllegalStateException("Empty level, side=" + expectedSide + ", price=" + price);
            for (Order order : queue) {
                if (order.getSide() != expectedSide) throw new IllegalStateException("Side mismatch, orderId=" + order.getOrderId());
                if (order.getPrice() != price) throw new IllegalStateException("Price mismatch, orderId=" + order.getOrderId());
                if (order.isTerminal()) throw new IllegalStateException("Terminal in level, orderId=" + order.getOrderId());
                order.validateState();
            }
        });
    }
}
