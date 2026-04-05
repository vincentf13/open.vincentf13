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

import java.util.ArrayDeque;
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
    // volatile snapshot array：matching thread 新增 OrderBook 後原子發布，flusher thread 安全遍歷
    private static volatile OrderBook[] INSTANCES_ARRAY = new OrderBook[0];

    public static OrderBook get(int symbolId) {
        OrderBook existing = INSTANCES.get(symbolId);
        if (existing != null) return existing;
        Symbol s = Symbol.of(symbolId);
        if (s == null) throw new IllegalArgumentException("Unknown symbol: " + symbolId);
        OrderBook book = new OrderBook(symbolId, s.getBaseAssetId(), s.getQuoteAssetId());
        INSTANCES.put(symbolId, book);
        // 重建 snapshot array（volatile 發布給 flusher thread）
        INSTANCES_ARRAY = INSTANCES.values().toArray(new OrderBook[0]);
        return book;
    }

    public static OrderBook[] getInstances() { return INSTANCES_ARRAY; }
    public static void resetForRecovery() {
        INSTANCES.clear();
        INSTANCES_ARRAY = new OrderBook[0];
    }

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

    // 批次寫入緩衝（雙緩衝：matching 寫 active，flusher 讀 draining）
    private final Long2ObjectHashMap<Order> ordersBufA = new Long2ObjectHashMap<>(65536, 0.5f);
    private final Long2ObjectHashMap<Order> ordersBufB = new Long2ObjectHashMap<>(65536, 0.5f);
    private final Long2ObjectHashMap<Trade> tradesBufA = new Long2ObjectHashMap<>(65536, 0.5f);
    private final Long2ObjectHashMap<Trade> tradesBufB = new Long2ObjectHashMap<>(65536, 0.5f);
    private final LongHashSet addsBufA = new LongHashSet(32768), addsBufB = new LongHashSet(32768);
    private final LongHashSet removalsBufA = new LongHashSet(32768), removalsBufB = new LongHashSet(32768);

    private Long2ObjectHashMap<Order> activeOrders = ordersBufA;
    private Long2ObjectHashMap<Trade> activeTrades = tradesBufA;
    private LongHashSet activeAdds = addsBufA;
    private LongHashSet activeRemovals = removalsBufA;

    // volatile：matching 發布 draining 指針給 flusher 讀取
    private volatile Long2ObjectHashMap<Order> drainingOrders = null;
    private volatile Long2ObjectHashMap<Trade> drainingTrades = null;
    private volatile LongHashSet drainingAdds = null;
    private volatile LongHashSet drainingRemovals = null;

    // 每-緩衝 snap 物件池（零分配重用）
    // 設計：每個緩衝在 active 時由 matching thread 借取；成為 draining 後由 flusher thread 獨佔寫回。
    // 歸還與借取是隔離的（同一時刻只有一個 thread 訪問 buffer 的 pool），無需同步。
    private final ArrayDeque<Order> orderSnapPoolA = new ArrayDeque<>(16384);
    private final ArrayDeque<Order> orderSnapPoolB = new ArrayDeque<>(16384);
    private final ArrayDeque<Trade> tradePoolA = new ArrayDeque<>(16384);
    private final ArrayDeque<Trade> tradePoolB = new ArrayDeque<>(16384);

    // 撮合結構
    private final Long2ObjectRBTreeMap<Deque<Order>> bids = new Long2ObjectRBTreeMap<>((LongComparator) (k1, k2) -> Long.compare(k2, k1));
    private final Long2ObjectRBTreeMap<Deque<Order>> asks = new Long2ObjectRBTreeMap<>();
    private final Long2ObjectHashMap<Deque<Order>> bidLevels = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY, 0.5f);
    private final Long2ObjectHashMap<Deque<Order>> askLevels = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY, 0.5f);
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_ORDER_COUNT, 0.5f);
    private final Long2ObjectHashMap<LongHashSet> userOrdersIndex = new Long2ObjectHashMap<>(4096, 0.5f);

    // Flush 用可重用 Key（flusher thread 專用）
    private final LongValue fkO = new LongValue();
    private final LongValue fkT = new LongValue();
    private final LongValue fkA = new LongValue();

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
            ArrayDeque<Trade> tPool = (activeTrades == tradesBufA) ? tradePoolA : tradePoolB;
            Trade t = tPool.pollFirst();
            if (t == null) t = new Trade();
            t.setTradeId(progress.nextTradeId()); t.setOrderId(maker.getOrderId());
            t.setPrice(price); t.setQty(matchQty); t.setTime(timestamp); t.setLastSeq(gwSeq);
            activeTrades.put(t.getTradeId(), t);

            maker.setFilled(maker.getFilled() + matchQty);
            taker.setFilled(taker.getFilled() + matchQty);
            StaticMetricsHolder.addCounter(MetricsKey.MATCH_COUNT, 1);
            finalizer.onMatch(t, maker, taker, baseAssetId, quoteAssetId);

            if (maker.remainingQty() == 0) {
                finalizeOrder(maker, gwSeq); makers.pollFirst();
                MatchingPool.releaseOrder(maker); // maker 終局後不再被 match 迴圈讀取，安全回池
            }
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
        // 池回收：由呼叫端在讀完 `o` 後執行 MatchingPool.releaseOrder(o)，
        // 避免 cancel 路徑中 caller 繼續讀取已回池物件的風險。
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
        // 深拷貝快照：切斷 matching 對 o 的後續 mutation 與 flusher 讀取的 race，
        // 避免撕裂寫入導致磁碟上出現 "NEW order with filled>0" 這類無法 validateState 的狀態。
        // snap 物件從 active 緩衝專屬 pool 借取，歸還由 flusher 在 drainToDisk 中完成。
        ArrayDeque<Order> pool = (activeOrders == ordersBufA) ? orderSnapPoolA : orderSnapPoolB;
        Order snap = pool.pollFirst();
        if (snap == null) snap = new Order();
        snap.copyFrom(o);
        activeOrders.put(o.getOrderId(), snap);
        if (!o.isTerminal()) { activeAdds.add(o.getOrderId()); activeRemovals.remove(o.getOrderId()); }
        else { activeRemovals.add(o.getOrderId()); activeAdds.remove(o.getOrderId()); }
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

    /** matching thread 呼叫：將 active 四個緩衝翻轉為 draining，指針操作 ns 級 */
    public boolean rotate() {
        if (drainingOrders != null) return false;  // flusher 尚未完成上一輪
        if (activeOrders.isEmpty() && activeTrades.isEmpty()
                && activeAdds.isEmpty() && activeRemovals.isEmpty()) return false;
        // 先儲存 active 引用
        Long2ObjectHashMap<Order> wO = activeOrders;
        Long2ObjectHashMap<Trade> wT = activeTrades;
        LongHashSet wAdd = activeAdds;
        LongHashSet wRem = activeRemovals;
        // 翻轉 active 至另一組緩衝
        activeOrders = (wO == ordersBufA) ? ordersBufB : ordersBufA;
        activeTrades = (wT == tradesBufA) ? tradesBufB : tradesBufA;
        activeAdds = (wAdd == addsBufA) ? addsBufB : addsBufA;
        activeRemovals = (wRem == removalsBufA) ? removalsBufB : removalsBufA;
        // 發布 draining 指針（volatile store 保證所有先前 mutation 對 flusher 可見）
        drainingTrades = wT;
        drainingAdds = wAdd;
        drainingRemovals = wRem;
        drainingOrders = wO;  // 最後設定 orders，作為 flusher 的 happens-before 門檻
        return true;
    }

    /** flusher thread 呼叫：將 draining 緩衝寫入 ChronicleMap，完成後釋放 */
    public void drainToDisk() {
        Long2ObjectHashMap<Order> dO = drainingOrders;
        if (dO == null) return;  // 無待落盤資料
        Long2ObjectHashMap<Trade> dT = drainingTrades;
        LongHashSet dAdd = drainingAdds;
        LongHashSet dRem = drainingRemovals;

        // 歸還目標 pool（draining 緩衝專屬，與 active 隔離）
        ArrayDeque<Order> retOrderPool = (dO == ordersBufA) ? orderSnapPoolA : orderSnapPoolB;
        ArrayDeque<Trade> retTradePool = (dT == tradesBufA) ? tradePoolA : tradePoolB;
        if (!dO.isEmpty()) {
            dO.forEach((id, o) -> {
                fkO.set(id);
                ordersDisk.put(fkO, o);
                retOrderPool.addLast(o);
            });
            dO.clear();
        }
        if (!dT.isEmpty()) {
            dT.forEach((id, t) -> {
                fkT.set(id);
                tradesDisk.put(fkT, t);
                retTradePool.addLast(t);
            });
            dT.clear();
        }
        if (!dAdd.isEmpty()) {
            dAdd.forEach(id -> { fkA.set(id); activeDisk.put(fkA, Boolean.TRUE); });
            dAdd.clear();
        }
        if (!dRem.isEmpty()) {
            dRem.forEach(id -> { fkA.set(id); activeDisk.remove(fkA); });
            dRem.clear();
        }
        // 釋放順序與 rotate 相反：先清其他，最後清 orders（發布給 matching 下一輪 rotate）
        drainingTrades = null;
        drainingAdds = null;
        drainingRemovals = null;
        drainingOrders = null;
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
