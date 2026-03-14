package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 內存訂單簿 (OrderBook)
 職責：領域狀態機，管理撮合、持久化同步與容器回收
 */
@Slf4j
public class OrderBook {
    private static final Int2ObjectHashMap<OrderBook> INSTANCES = new Int2ObjectHashMap<>();
    
    // 全域共享物件池：加固為執行緒安全容器
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

    private final int symbolId;
    private final int baseAssetId;
    private final int quoteAssetId;
    
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();
    private final ChronicleMap<Long, Boolean> activeOrderIdDiskMap = Storage.self().activeOrders();
    private final ChronicleMap<Long, byte[]> userActiveOrdersDiskMap = Storage.self().userActiveOrders();
    private final ChronicleMap<Long, Trade> tradeHistoryDiskMap = Storage.self().trades();
    
    // 預分配 ByteBuffer 以減少 GC
    private final java.nio.ByteBuffer idBuffer = java.nio.ByteBuffer.allocate(1024);

    /** 
      雙索引結構優化：
      TreeMap 提供有序性 (用於撮合)
      Long2ObjectHashMap 提供 $O(1)$ 查找 (用於快速插入與更新)
     */
    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();
    private final Long2ObjectHashMap<Deque<Order>> priceLevelIndex = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY, 0.5f);
    
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_ORDER_COUNT, 0.5f); 

    private final Trade reusableTrade = new Trade();
    private static final Order SCAN_REUSABLE = new Order();

    public interface TradeFinalizer {
        void onMatch(long tradeId, Order maker, long price, long qty, int baseAsset, int quoteAsset);
    }

    private OrderBook(int symbolId, int baseAssetId, int quoteAssetId) {
        this.symbolId = symbolId;
        this.baseAssetId = baseAssetId;
        this.quoteAssetId = quoteAssetId;
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

    public Order handleCreate(long orderId, OrderCreateDecoder sbe, long clientOrderId, long gwSeq, 
                             Supplier<Long> tradeIdSupplier, TradeFinalizer finalizer) {
        Order taker = borrowAndFill(orderId, sbe, clientOrderId, gwSeq);
        match(taker, gwSeq, sbe.timestamp(), tradeIdSupplier, finalizer);
        syncOrder(taker, gwSeq);
        return taker;
    }

    private Order borrowAndFill(long orderId, OrderCreateDecoder sbe, long clientOrderId, long gwSeq) {
        Order o = borrowOrder();
        o.setOrderId(orderId); o.setUserId(sbe.userId()); o.setSymbolId(sbe.symbolId());
        o.setPrice(sbe.price()); o.setQty(sbe.qty()); o.setFilled(0);
        o.setSide((byte)(sbe.side() == Side.BUY ? OrderSide.BUY : OrderSide.SELL));
        o.setStatus((byte)0); o.setVersion(1); o.setLastSeq(gwSeq);
        o.setClientOrderId(clientOrderId);
        return o;
    }

    public static Collection<OrderBook> getInstances() {
        return INSTANCES.values();
    }

    public void saveState(net.openhft.chronicle.bytes.BytesOut<?> bytes) {
        bytes.writeInt(symbolId);
        bytes.writeInt(baseAssetId);
        bytes.writeInt(quoteAssetId);

        // 備份掛單數
        bytes.writeInt(orderIndex.size());
        
        // 分別備份 bids 與 asks
        saveSide(bytes, bids);
        saveSide(bytes, asks);
    }

    private void saveSide(net.openhft.chronicle.bytes.BytesOut<?> bytes, TreeMap<Long, Deque<Order>> side) {
        bytes.writeInt(side.size()); // 價位檔位數
        side.forEach((price, orders) -> {
            bytes.writeLong(price);
            bytes.writeInt(orders.size());
            for (Order o : orders) {
                o.writeMarshallable(bytes);
            }
        });
    }

    public void loadState(net.openhft.chronicle.bytes.BytesIn<?> bytes) {
        // 1. 清理當前狀態 (如果需要)
        clear();
        
        // 2. 讀取基礎屬性 (略過，因為 Instance 已建立)
        bytes.readInt(); bytes.readInt(); bytes.readInt();
        int totalOrders = bytes.readInt();

        // 3. 讀取 Bids & Asks
        loadSide(bytes, bids);
        loadSide(bytes, asks);
        
        log.info("訂單簿 {} 恢復完成：共加載 {} 筆掛單", symbolId, totalOrders);
    }

    private void loadSide(net.openhft.chronicle.bytes.BytesIn<?> bytes, TreeMap<Long, Deque<Order>> side) {
        int levelCount = bytes.readInt();
        for (int i = 0; i < levelCount; i++) {
            long price = bytes.readLong();
            int ordersAtLevel = bytes.readInt();
            Deque<Order> level = GLOBAL_DEQUE_POOL.pollFirst();
            if (level == null) level = new ArrayDeque<>(MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY);
            
            for (int j = 0; j < ordersAtLevel; j++) {
                Order o = borrowOrder();
                o.readMarshallable(bytes);
                level.addLast(o);
                orderIndex.put(o.getOrderId(), o);
            }
            side.put(price, level);
            priceLevelIndex.put(price, level);
        }
    }

    private void clear() {
        orderIndex.values().forEach(this::releaseOrder);
        orderIndex.clear();
        bids.values().forEach(level -> { level.clear(); GLOBAL_DEQUE_POOL.addLast(level); });
        bids.clear();
        asks.values().forEach(level -> { level.clear(); GLOBAL_DEQUE_POOL.addLast(level); });
        asks.clear();
        priceLevelIndex.clear();
    }

    public static void rebuildAll() {
        // 此方法已由 Snapshot 加載機制取代，僅作為冷啟動兜底
        ChronicleMap<Long, Order> allOrders = Storage.self().orders();
        Storage.self().activeOrders().forEach((id, active) -> {
            Order o = allOrders.getUsing(id, SCAN_REUSABLE);
            if (o != null && o.getStatus() < 2) {
                OrderBook.get(o.getSymbolId()).recoverOrder(o);
            }
        });
    }

    /**
      活躍訂單索引重建 (校準機制)
      遍歷所有訂單表，重新計算並更新 activeOrders 與 userActiveOrders
     */
    public static void rebuildActiveOrdersIndexes() {
        log.info("--- 開始重建活躍訂單索引 ---");
        ChronicleMap<Long, Order> allOrders = Storage.self().orders();
        ChronicleMap<Long, Boolean> activeIds = Storage.self().activeOrders();
        ChronicleMap<Long, byte[]> userActive = Storage.self().userActiveOrders();
        
        // 1. 清理舊索引
        activeIds.clear();
        userActive.clear();
        
        // 2. 遍歷訂單表
        allOrders.forEach((id, order) -> {
            if (order.getStatus() < 2) { // NEW 或 PARTIALLY_FILLED
                activeIds.put(id, Boolean.TRUE);
                
                long uid = order.getUserId();
                byte[] current = userActive.get(uid);
                if (current == null) {
                    byte[] newBytes = new byte[8];
                    java.nio.ByteBuffer.wrap(newBytes).putLong(id);
                    userActive.put(uid, newBytes);
                } else {
                    byte[] updated = new byte[current.length + 8];
                    System.arraycopy(current, 0, updated, 0, current.length);
                    java.nio.ByteBuffer.wrap(updated, current.length, 8).putLong(id);
                    userActive.put(uid, updated);
                }
            }
        });
        log.info("✅ 活躍訂單索引重建完成。");
    }

    private void match(Order taker, long gwSeq, long timestamp, Supplier<Long> tradeIdSupplier, TradeFinalizer finalizer) {
        final boolean isBuy = taker.getSide() == OrderSide.BUY;
        final TreeMap<Long, Deque<Order>> counterSide = isBuy ? asks : bids;
        final long takerPrice = taker.getPrice();
        final long takerUserId = taker.getUserId();

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
                
                reusableTrade.setTradeId(tid); reusableTrade.setOrderId(maker.getOrderId());
                reusableTrade.setPrice(bestPrice); reusableTrade.setQty(matchQty);
                reusableTrade.setTime(timestamp); reusableTrade.setLastSeq(gwSeq);
                tradeHistoryDiskMap.put(tid, reusableTrade);

                maker.setFilled(maker.getFilled() + matchQty);
                taker.setFilled(taker.getFilled() + matchQty);
                finalizer.onMatch(tid, maker, bestPrice, matchQty, baseAssetId, quoteAssetId);

                if (maker.getFilled() == maker.getQty()) {
                    syncOrder(maker, gwSeq);
                    orderIndex.remove(maker.getOrderId());
                    releaseOrder(makers.pollFirst()); 
                } else {
                    syncOrder(maker, gwSeq);
                    break;
                }
            }
            if (makers != null && makers.isEmpty()) {
                counterSide.remove(bestPrice);
                priceLevelIndex.remove(bestPrice); // 同步移除快速索引
                makers.clear(); // 清理引用，防止記憶體洩漏
                GLOBAL_DEQUE_POOL.addLast(makers); 
            } else break;
        }
        if (taker.getQty() > taker.getFilled()) add(taker);
    }

    private void add(Order order) {
        final long price = order.getPrice();
        // 優化：先從 $O(1)$ 快速索引查找價位容器
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
    }

    public void syncOrder(Order o, long gwSeq) {
        if (o.getFilled() == o.getQty()) o.setStatus((byte) 2);
        else if (o.getFilled() > 0) o.setStatus((byte) 1);
        o.setVersion(o.getVersion() + 1); o.setLastSeq(gwSeq);
        
        allOrdersDiskMap.put(o.getOrderId(), o);
        
        // --- 核心修正：使用二進制結構維護活躍訂單 ID 清單 ---
        final long uid = o.getUserId();
        final long oid = o.getOrderId();
        
        if (o.getStatus() < 2) {
            activeOrderIdDiskMap.put(oid, Boolean.TRUE);
            byte[] currentBytes = userActiveOrdersDiskMap.get(uid);
            if (currentBytes == null) {
                byte[] newBytes = new byte[8];
                java.nio.ByteBuffer.wrap(newBytes).putLong(oid);
                userActiveOrdersDiskMap.put(uid, newBytes);
            } else {
                // 檢查是否已存在 (避免重複)
                boolean exists = false;
                for (int i = 0; i < currentBytes.length; i += 8) {
                    if (java.nio.ByteBuffer.wrap(currentBytes, i, 8).getLong() == oid) {
                        exists = true; break;
                    }
                }
                if (!exists) {
                    byte[] updatedBytes = new byte[currentBytes.length + 8];
                    System.arraycopy(currentBytes, 0, updatedBytes, 0, currentBytes.length);
                    java.nio.ByteBuffer.wrap(updatedBytes, currentBytes.length, 8).putLong(oid);
                    userActiveOrdersDiskMap.put(uid, updatedBytes);
                }
            }
        } else {
            activeOrderIdDiskMap.remove(oid);
            byte[] currentBytes = userActiveOrdersDiskMap.get(uid);
            if (currentBytes != null) {
                int targetIndex = -1;
                for (int i = 0; i < currentBytes.length; i += 8) {
                    if (java.nio.ByteBuffer.wrap(currentBytes, i, 8).getLong() == oid) {
                        targetIndex = i; break;
                    }
                }
                if (targetIndex != -1) {
                    if (currentBytes.length == 8) {
                        userActiveOrdersDiskMap.remove(uid);
                    } else {
                        byte[] updatedBytes = new byte[currentBytes.length - 8];
                        System.arraycopy(currentBytes, 0, updatedBytes, 0, targetIndex);
                        System.arraycopy(currentBytes, targetIndex + 8, updatedBytes, targetIndex, currentBytes.length - targetIndex - 8);
                        userActiveOrdersDiskMap.put(uid, updatedBytes);
                    }
                }
            }
        }
    }

    public void remove(long orderId) { 
        Order o = orderIndex.remove(orderId); 
        if (o != null) {
            // 從價位佇列中徹底移除，防止「幽靈訂單」殘留
            Deque<Order> level = priceLevelIndex.get(o.getPrice());
            if (level != null) {
                level.remove(o);
                if (level.isEmpty()) {
                    priceLevelIndex.remove(o.getPrice());
                    if (o.getSide() == OrderSide.BUY) {
                        bids.remove(o.getPrice());
                    } else {
                        asks.remove(o.getPrice());
                    }
                    GLOBAL_DEQUE_POOL.addLast(level);
                }
            }
            releaseOrder(o);
        }
    }

    public int getBaseAssetId() { return baseAssetId; }
    public int getQuoteAssetId() { return quoteAssetId; }
}
