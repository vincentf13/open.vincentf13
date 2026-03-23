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
import java.util.concurrent.atomic.AtomicLong;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 內存訂單簿 (OrderBook) - 極致性能優化版
 */
@Slf4j
public class OrderBook {
    public static final AtomicLong TOTAL_MATCH_COUNT = new AtomicLong(0);
    private static final Int2ObjectHashMap<OrderBook> INSTANCES = new Int2ObjectHashMap<>();
    
    // 性能優化：改用非同步安全但無 Node 分配開銷的池容器 (撮合引擎為單執行緒)
    private static final Deque<Order> GLOBAL_ORDER_POOL = new ArrayDeque<>(200_000);
    private static final Deque<Trade> GLOBAL_TRADE_POOL = new ArrayDeque<>(20_000);
    private static final Deque<open.vincentf13.service.spot.model.CidKey> GLOBAL_CID_POOL = new ArrayDeque<>(20_000);
    private static final Deque<Deque<Order>> GLOBAL_DEQUE_POOL = new ArrayDeque<>(10_000);

    static {
        // 性能優化：預分配物件，減少啟動後 GC
        for (int i = 0; i < 200_000; i++) {
            GLOBAL_ORDER_POOL.add(new Order());
            if (i < 20_000) {
                GLOBAL_TRADE_POOL.add(new Trade());
                GLOBAL_CID_POOL.add(new open.vincentf13.service.spot.model.CidKey());
            }
            if (i < 10_000) {
                GLOBAL_DEQUE_POOL.add(new ArrayDeque<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY));
            }
        }
    }

    public static open.vincentf13.service.spot.model.CidKey borrowCid() {
        open.vincentf13.service.spot.model.CidKey cid = GLOBAL_CID_POOL.pollFirst();
        return (cid == null) ? new open.vincentf13.service.spot.model.CidKey() : cid;
    }

    public static void releaseCid(open.vincentf13.service.spot.model.CidKey cid) { if (cid != null) GLOBAL_CID_POOL.addLast(cid); }

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
    private final ChronicleMap<Long, Trade> tradeHistoryDiskMap = Storage.self().trades();
    
    private final Long2ObjectHashMap<Order> pendingOrders = new Long2ObjectHashMap<>(16384, 0.5f);
    private final Long2ObjectHashMap<Trade> pendingTrades = new Long2ObjectHashMap<>(32768, 0.5f);
    private final LongHashSet pendingActiveRemovals = new LongHashSet(8192);
    private final LongHashSet pendingActiveAdds = new LongHashSet(8192);

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
        if (!pendingOrders.isEmpty()) { 
            pendingOrders.forEach((id, o) -> {
                allOrdersDiskMap.put(id, o);
                if (o.getStatus() >= 2) releaseOrder(o);
            });
            pendingOrders.clear(); 
        }
        if (!pendingTrades.isEmpty()) { 
            pendingTrades.forEach((id, t) -> {
                tradeHistoryDiskMap.put(id, t);
                releaseTrade(t);
            }); 
            pendingTrades.clear(); 
        }
        if (!pendingActiveAdds.isEmpty()) { pendingActiveAdds.forEach(id -> activeOrderIdDiskMap.put(id, Boolean.TRUE)); pendingActiveAdds.clear(); }
        if (!pendingActiveRemovals.isEmpty()) { pendingActiveRemovals.forEach(activeOrderIdDiskMap::remove); pendingActiveRemovals.clear(); }
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

    private Trade borrowTrade() {
        Trade t = GLOBAL_TRADE_POOL.pollFirst();
        return (t == null) ? new Trade() : t;
    }

    private void releaseTrade(Trade t) { if (t != null) GLOBAL_TRADE_POOL.addLast(t); }

    public Order handleCreate(long orderId, long userId, int symbolId, long price, long qty, Side side, long clientOrderId, long timestamp, long gwSeq,
                             open.vincentf13.service.spot.model.WalProgress progress, TradeFinalizer finalizer) {
        Order taker = borrowAndFill(orderId, userId, symbolId, price, qty, side, clientOrderId, gwSeq);
        match(taker, gwSeq, timestamp, progress, finalizer);
        syncOrder(taker, gwSeq);
        return taker;
    }

    private Order borrowAndFill(long orderId, long userId, int symbolId, long price, long qty, Side side, long clientOrderId, long gwSeq) {
        Order o = borrowOrder();
        o.fill(orderId, userId, symbolId, price, qty, (byte)(side == Side.BUY ? OrderSide.BUY : OrderSide.SELL), clientOrderId, gwSeq);
        return o;
    }

    private void match(Order taker, long gwSeq, long timestamp, open.vincentf13.service.spot.model.WalProgress progress, TradeFinalizer finalizer) {
        final boolean isBuy = taker.getSide() == OrderSide.BUY;
        final TreeMap<Long, Deque<Order>> counterSide = isBuy ? asks : bids;
        final long takerPrice = taker.getPrice();

        while (taker.getQty() > taker.getFilled()) {
            if (counterSide.isEmpty()) break;
            
            final long bestPrice = counterSide.firstKey();
            if (isBuy ? (takerPrice < bestPrice) : (takerPrice > bestPrice)) break;

            executeMatchAtLevel(taker, bestPrice, counterSide, gwSeq, timestamp, progress, finalizer);
        }
        
        if (taker.getQty() > taker.getFilled()) {
            add(taker);
        } else {
            taker.setStatus((byte) 2); // FILLED
        }
    }

    private void executeMatchAtLevel(Order taker, long price, TreeMap<Long, Deque<Order>> counterSide, long gwSeq, long timestamp, 
                                   open.vincentf13.service.spot.model.WalProgress progress, TradeFinalizer finalizer) {
        final Deque<Order> makers = priceLevelIndex.get(price);
        if (makers == null || makers.isEmpty()) {
            counterSide.remove(price);
            priceLevelIndex.remove(price);
            return;
        }

        while (!makers.isEmpty() && taker.getQty() > taker.getFilled()) {
            Order maker = makers.peekFirst();
            // 檢查訂單是否依然有效 (防範非同步刪除或狀態異常)
            if (!orderIndex.containsKey(maker.getOrderId())) {
                makers.pollFirst(); 
                continue; 
            }

            long matchQty = Math.min(taker.getQty() - taker.getFilled(), maker.getQty() - maker.getFilled());
            long tid = progress.getAndIncrTradeId();
            
            // 建立成交記錄
            Trade t = borrowTrade();
            t.setTradeId(tid); t.setOrderId(maker.getOrderId());
            t.setPrice(price); t.setQty(matchQty);
            t.setTime(timestamp); t.setLastSeq(gwSeq);
            pendingTrades.put(tid, t);

            // 更新數量
            maker.setFilled(maker.getFilled() + matchQty);
            taker.setFilled(taker.getFilled() + matchQty);
            
            TOTAL_MATCH_COUNT.incrementAndGet();
            finalizer.onMatch(tid, maker, price, matchQty, baseAssetId, quoteAssetId);

            if (maker.getFilled() == maker.getQty()) {
                finalizeMakerFilled(maker, gwSeq);
                makers.pollFirst(); 
            } else {
                syncOrder(maker, gwSeq);
                break;
            }
        }
        
        if (makers.isEmpty()) {
            cleanupEmptyLevel(price, counterSide, makers);
        }
    }

    private void finalizeMakerFilled(Order maker, long gwSeq) {
        syncOrder(maker, gwSeq);
        orderIndex.remove(maker.getOrderId());
        LongHashSet set = userOrderIdsIndex.get(maker.getUserId());
        if (set != null) set.remove(maker.getOrderId());
    }

    private void cleanupEmptyLevel(long price, TreeMap<Long, Deque<Order>> counterSide, Deque<Order> makers) {
        counterSide.remove(price);
        priceLevelIndex.remove(price);
        makers.clear();
        GLOBAL_DEQUE_POOL.addLast(makers); 
    }

    private void add(Order order) {
        final long price = order.getPrice();
        Deque<Order> level = priceLevelIndex.get(price);
        if (level == null) {
            TreeMap<Long, Deque<Order>> levels = (order.getSide() == OrderSide.BUY) ? bids : asks;
            level = GLOBAL_DEQUE_POOL.pollFirst();
            if (level == null) level = new ArrayDeque<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY);
            else level.clear();
            
            levels.put(price, level);
            priceLevelIndex.put(price, level);
        }
        level.addLast(order);
        orderIndex.put(order.getOrderId(), order);
        
        long uid = order.getUserId();
        LongHashSet set = userOrderIdsIndex.get(uid);
        if (set == null) {
            set = new LongHashSet(16);
            userOrderIdsIndex.put(uid, set);
        }
        set.add(order.getOrderId());
    }

    public void syncOrder(Order o, long gwSeq) {
        // 1. 自動狀態機轉換
        final long filled = o.getFilled(), qty = o.getQty();
        o.setStatus((byte) (filled == qty ? 2 : (filled > 0 ? 1 : 0)));
        o.setVersion(o.getVersion() + 1);
        o.setLastSeq(gwSeq);
        
        // 2. 更新持久化緩衝與內存索引
        final long oid = o.getOrderId(), uid = o.getUserId();
        pendingOrders.put(oid, o);
        
        LongHashSet userOrders = userOrderIdsIndex.computeIfAbsent(uid, k -> new LongHashSet(16));
        if (o.getStatus() < 2) { // NEW, PARTIAL
            pendingActiveAdds.add(oid);
            pendingActiveRemovals.remove(oid);
            userOrders.add(oid);
        } else { // FILLED, CANCELED
            pendingActiveRemovals.add(oid);
            pendingActiveAdds.remove(oid);
            userOrders.remove(oid);
        }
    }

    public void remove(long orderId) { 
        Order o = orderIndex.remove(orderId); 
        if (o == null) return;

        Deque<Order> level = priceLevelIndex.get(o.getPrice());
        if (level != null) {
            level.remove(o);
            if (level.isEmpty()) {
                cleanupEmptyLevel(o.getPrice(), (o.getSide() == OrderSide.BUY) ? bids : asks, level);
            }
        }
        
        o.setStatus((byte) 3); // CANCELED
        syncOrder(o, o.getLastSeq());
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
