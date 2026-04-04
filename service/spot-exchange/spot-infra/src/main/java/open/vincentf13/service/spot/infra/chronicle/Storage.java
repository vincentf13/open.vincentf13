package open.vincentf13.service.spot.infra.chronicle;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import open.vincentf13.service.spot.infra.Constants.ChronicleMapEnum;
import open.vincentf13.service.spot.infra.Constants.ChronicleQueueEnum;
import open.vincentf13.service.spot.infra.alloc.PreTouchUtil;
import open.vincentf13.service.spot.model.*;

import java.io.File;
import java.io.IOException;

/**
 * 系統存儲中心 (Storage Hub) - 穩定版
 * 職責：管理所有 Chronicle 資源，並針對 Windows 檔案鎖定進行優化。
 */
@Slf4j
public class Storage {
    private static volatile Storage INSTANCE;
    private volatile boolean closed;

    // 單例取得改為延遲加載 (Lazy Init)，防止 Spring 啟動時因為檔案鎖定而卡死 main 線程
    public static synchronized Storage self() {
        if (INSTANCE == null) INSTANCE = new Storage();
        return INSTANCE;
    }

    private final ChronicleMap<LongValue, Order> orders;
    private final ChronicleMap<LongValue, Trade> trades;
    private final ChronicleMap<BalanceKey, Balance> balances;
    private final ChronicleMap<LongValue, LongValue> userAssets;
    private final ChronicleMap<LongValue, Boolean> activeOrders;
    private final ChronicleMap<CidKey, LongValue> cids;
    private final ChronicleMap<Byte, MsgProgress> msgMetadata;
    private final ChronicleMap<Byte, WalProgress> walMetadata;
    private final ChronicleMap<Long, Long> latestMetrics;
    private final ChronicleMap<Long, Long> tpsHistory;
    private final ChronicleMap<Long, Long> latencyHistory;
    private final ChronicleMap<Long, String> gcEventHistory;

    private final ChronicleQueue gatewaySenderWal;

    private Storage() {
        this.closed = false;
        log.info(">>> [STORAGE] 正在啟動 Chronicle 資源加載...");
        try {
            this.orders = createMap(ChronicleMapEnum.ORDERS, LongValue.class, Order.class, 10_000_000, 128);
            this.trades = createMap(ChronicleMapEnum.TRADES, LongValue.class, Trade.class, 10_000_000, 64);
            this.balances = createMap(ChronicleMapEnum.BALANCES, BalanceKey.class, Balance.class, 10_000_000, 16, 64);
            this.userAssets = createMap(ChronicleMapEnum.USER_ASSETS, LongValue.class, LongValue.class, 1_000_000, 8);
            this.activeOrders = createMap(ChronicleMapEnum.ACTIVE_ORDERS, LongValue.class, Boolean.class, 10_000_000, 1);
            this.cids = createMap(ChronicleMapEnum.CIDS, CidKey.class, LongValue.class, 10_000_000, 16, 8);
            this.msgMetadata = createMap("msg-" + ChronicleMapEnum.METADATA, Byte.class, MsgProgress.class, 10, 32);
            this.walMetadata = createMap("wal-" + ChronicleMapEnum.METADATA, Byte.class, WalProgress.class, 10, 32);
            
            // 重要：這些 Map 如果在 Windows 上被多個 Java 進程同時訪問且沒有設置正確的 entries 空間，會拋出 Exception
            this.latestMetrics = createMap("metrics-latest", Long.class, Long.class, 4096, 8, 8);
            this.tpsHistory = createMap("metrics-tps-history", Long.class, Long.class, 86400 * 7, 8, 8); 
            this.latencyHistory = createMap("metrics-latency-history", Long.class, Long.class, 86400 * 7, 8, 8); 
            this.gcEventHistory = createMap("metrics-gc-event-history", Long.class, String.class, 2048, 8, 256);
            
            this.gatewaySenderWal = createQueue(ChronicleQueueEnum.CLIENT_TO_GW);

            // 預熱：讀取所有 mmap 頁面，強迫 OS 分配實體 RAM，消除運行時 Page Fault
            preTouchAll();
            log.info(">>> [STORAGE] Chronicle 所有資源初始化成功。");
        } catch (Exception e) {
            log.error(">>> [STORAGE-ERROR] Chronicle 初始化期間發生嚴重錯誤！可能是檔案被其他進程佔用。", e);
            throw e;
        }
    }

    public ChronicleMap<LongValue, Order> orders() { return orders; }
    public ChronicleMap<LongValue, Trade> trades() { return trades; }
    public ChronicleMap<BalanceKey, Balance> balances() { return balances; }
    public ChronicleMap<LongValue, LongValue> userAssets() { return userAssets; }
    public ChronicleMap<LongValue, Boolean> activeOrders() { return activeOrders; }
    public ChronicleMap<CidKey, LongValue> clientOrderIdMap() { return cids; }
    public ChronicleMap<Byte, MsgProgress> msgProgressMetadata() { return msgMetadata; }
    public ChronicleMap<Byte, WalProgress> walMetadata() { return walMetadata; }
    public ChronicleMap<Long, Long> latestMetrics() { return latestMetrics; }
    public ChronicleMap<Long, Long> tpsHistory() { return tpsHistory; }
    public ChronicleMap<Long, Long> latencyHistory() { return latencyHistory; }
    public ChronicleMap<Long, String> gcEventHistory() { return gcEventHistory; }
    public ChronicleQueue gatewaySenderWal() { return gatewaySenderWal; }
    public ChronicleQueue openGatewaySenderWal() { return createQueue(ChronicleQueueEnum.CLIENT_TO_GW); }

    // 輔助方法：5 個引數的重載
    private <K, V> ChronicleMap<K, V> createMap(Object name, Class<K> keyCls, Class<V> valCls, int entries, int valSize) {
        return createMap(name, keyCls, valCls, entries, 0, valSize);
    }

    // 核心方法：6 個引數
    private <K, V> ChronicleMap<K, V> createMap(Object name, Class<K> keyCls, Class<V> valCls, int entries, int keySize, int valSize) {
        String dir = ChronicleMapEnum.DEFAULT_BASE_DIR;
        new File(dir).mkdirs();
        File mapFile = new File(dir + name);
        
        try {
            var builder = ChronicleMap.of(keyCls, valCls).name(name.toString()).entries(entries);
            
            // 重要：對於固定長度類型 (如 Long)，averageKeySize 會拋出異常，必須 catch
            try {
                if (keyCls == Long.class || keyCls == long.class) builder.averageKeySize(8);
                else if (keySize > 0) builder.averageKeySize(keySize);
                else builder.averageKeySize(16);
            } catch (Exception ignored) {}

            try {
                if (valCls == Long.class || valCls == long.class) builder.averageValueSize(8);
                else if (valSize > 0) builder.averageValueSize(valSize);
                else builder.averageValueSize(32);
            } catch (Exception ignored) {}
            
            return builder.createPersistedTo(mapFile);
        } catch (IOException e) {
            log.error("無法開啟 ChronicleMap: {}，路徑: {}", name, mapFile.getAbsolutePath(), e);
            throw new RuntimeException("ChronicleMap 建立失敗: " + name, e);
        }
    }

    private ChronicleQueue createQueue(ChronicleQueueEnum q) {
        String dir = ChronicleMapEnum.WAL_BASE_DIR;
        new File(dir).mkdirs();
        return SingleChronicleQueueBuilder.single(dir + q.getPath())
                .rollCycle(net.openhft.chronicle.queue.RollCycles.FAST_DAILY)
                .blockSize(128 << 20) // 128MB 預分配塊，減少 OS 分頁時的停頓
                .build();
    }

    private void preTouchAll() {
        long t0 = System.nanoTime();
        File mapDir = new File(ChronicleMapEnum.DEFAULT_BASE_DIR);
        File walDir = new File(ChronicleMapEnum.WAL_BASE_DIR);

        long pages = PreTouchUtil.touchDirectory(mapDir) + PreTouchUtil.touchDirectory(walDir);
        long touchMs = (System.nanoTime() - t0) / 1_000_000;

        // mlock：嘗試鎖定至實體 RAM，防止 swap（需 OS 權限，失敗時 graceful degrade）
        long locked = PreTouchUtil.mlockDirectory(mapDir) + PreTouchUtil.mlockDirectory(walDir);
        long totalMs = (System.nanoTime() - t0) / 1_000_000;

        log.info(">>> [STORAGE] Pre-touch {} 頁 ({}MB, {}ms), mlock {} 頁 ({}ms)",
                pages, (pages * 4096) >> 20, touchMs, locked, totalMs - touchMs);
    }

    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            safeClose(orders); safeClose(trades); safeClose(balances); safeClose(userAssets);
            safeClose(activeOrders); safeClose(cids);
            safeClose(msgMetadata); safeClose(walMetadata); safeClose(latestMetrics);
            safeClose(tpsHistory); safeClose(latencyHistory); safeClose(gcEventHistory);
            if (gatewaySenderWal != null) gatewaySenderWal.close();
            INSTANCE = null;
        }
    }

    private void safeClose(ChronicleMap<?, ?> map) {
        if (map != null && !map.isClosed()) map.close();
    }
}
