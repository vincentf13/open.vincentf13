package open.vincentf13.service.spot.infra.chronicle;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import open.vincentf13.service.spot.infra.Constants.ChronicleMapEnum;
import open.vincentf13.service.spot.infra.Constants.ChronicleQueueEnum;
import open.vincentf13.service.spot.model.*;

import java.io.File;
import java.io.IOException;

/**
 * 系統存儲中心 (Storage Hub) - 高性能優化版
 * 職責：統一管理所有持久化 (Chronicle Map/Queue) 資源。
 * 優化：移除內存 RingBuffer 隊列，改由 Aeron 直連撮合。
 */
@Slf4j
public class Storage {
    private static final Storage INSTANCE = new Storage();
    public static Storage self() { return INSTANCE; }

    // --- 持久化 Maps (Final & Pre-initialized) ---
    private final ChronicleMap<LongValue, Order> orders;
    private final ChronicleMap<LongValue, Trade> trades;
    private final ChronicleMap<BalanceKey, Balance> balances;
    private final ChronicleMap<LongValue, LongValue> userAssets;
    private final ChronicleMap<LongValue, Boolean> activeOrders;
    private final ChronicleMap<CidKey, LongValue> cids;
    private final ChronicleMap<LongValue, byte[]> userActiveOrders;
    private final ChronicleMap<Byte, MsgProgress> msgMetadata;
    private final ChronicleMap<Byte, WalProgress> walMetadata;
    private final ChronicleMap<LongValue, LongValue> metricsHistory;

    // --- 持久化 Queues (WAL) ---
    private final ChronicleQueue gatewaySenderWal;

    private Storage() {
        log.info(">>> [INIT] 正在初始化 Chronicle 存儲資源...");
        this.orders = createMap(ChronicleMapEnum.ORDERS, LongValue.class, Order.class, 10_000_000, 128);
        this.trades = createMap(ChronicleMapEnum.TRADES, LongValue.class, Trade.class, 10_000_000, 64);
        this.balances = createMap(ChronicleMapEnum.BALANCES, BalanceKey.class, Balance.class, 10_000_000, 16, 64);
        this.userAssets = createMap(ChronicleMapEnum.USER_ASSETS, LongValue.class, LongValue.class, 1_000_000, 8);
        this.activeOrders = createMap(ChronicleMapEnum.ACTIVE_ORDERS, LongValue.class, Boolean.class, 10_000_000, 1);
        this.cids = createMap(ChronicleMapEnum.CIDS, CidKey.class, LongValue.class, 10_000_000, 16, 8);
        this.userActiveOrders = createMap(ChronicleMapEnum.USER_ACTIVE_ORDERS, LongValue.class, byte[].class, 100_000, 256);
        this.msgMetadata = createMap("msg-" + ChronicleMapEnum.METADATA, Byte.class, MsgProgress.class, 10, 0);
        this.walMetadata = createMap("wal-" + ChronicleMapEnum.METADATA, Byte.class, WalProgress.class, 10, 0);
        this.metricsHistory = createMap(ChronicleMapEnum.METRICS_HISTORY, LongValue.class, LongValue.class, 86400 * 2, 8, 8); // 保留 2 天指標
        
        this.gatewaySenderWal = createQueue(ChronicleQueueEnum.CLIENT_TO_GW);
        log.info(">>> [INIT] Chronicle 存儲資源初始化完成。");
    }

    // --- Getters (Thread-Safe by Final) ---
    public ChronicleMap<LongValue, Order> orders() { return orders; }
    public ChronicleMap<LongValue, Trade> trades() { return trades; }
    public ChronicleMap<BalanceKey, Balance> balances() { return balances; }
    public ChronicleMap<LongValue, LongValue> userAssets() { return userAssets; }
    public ChronicleMap<LongValue, Boolean> activeOrders() { return activeOrders; }
    public ChronicleMap<CidKey, LongValue> clientOrderIdMap() { return cids; }
    public ChronicleMap<LongValue, byte[]> userActiveOrders() { return userActiveOrders; }
    public ChronicleMap<Byte, MsgProgress> msgProgressMetadata() { return msgMetadata; }
    public ChronicleMap<Byte, WalProgress> walMetadata() { return walMetadata; }
    public ChronicleMap<LongValue, LongValue> metricsHistory() { return metricsHistory; }
    public ChronicleQueue gatewaySenderWal() { return gatewaySenderWal; }

    // --- Helpers ---

    private <K, V> ChronicleMap<K, V> createMap(Object name, Class<K> keyCls, Class<V> valCls, int entries, int valSize) {
        return createMap(name.toString(), keyCls, valCls, entries, 0, valSize);
    }

    private <K, V> ChronicleMap<K, V> createMap(Object name, Class<K> keyCls, Class<V> valCls, int entries, int keySize, int valSize) {
        try {
            String dir = ChronicleMapEnum.DEFAULT_BASE_DIR;
            new File(dir).mkdirs();
            var builder = ChronicleMap.of(keyCls, valCls).name(name.toString()).entries(entries);
            
            try {
                if (keySize > 0) builder.averageKeySize(keySize);
                else if (keyCls == String.class) builder.averageKeySize(32);
                else builder.averageKeySize(16);
            } catch (IllegalStateException ignored) {}

            try {
                if (valSize > 0) builder.averageValueSize(valSize);
                else if (valCls.isInterface()) builder.averageValueSize(32);
                else if (valCls == byte[].class) builder.averageValueSize(256);
                else builder.averageValueSize(64);
            } catch (IllegalStateException ignored) {}
            
            return builder.createPersistedTo(new File(dir + name));
        } catch (IOException e) {
            throw new RuntimeException("ChronicleMap 建立失敗: " + name, e);
        }
    }

    private ChronicleQueue createQueue(ChronicleQueueEnum q) {
        String dir = ChronicleMapEnum.WAL_BASE_DIR;
        new File(dir).mkdirs();
        return SingleChronicleQueueBuilder
                .single(dir + q.getPath())
                .rollCycle(net.openhft.chronicle.queue.RollCycles.FAST_DAILY)
                .indexCount(1024)
                .indexSpacing(256) // 恢復為 256 以保證讀取性能
                .syncMode(net.openhft.chronicle.bytes.SyncMode.ASYNC) 
                .build();
    }

    public void close() {
        log.info(">>> [CLOSE] 正在釋放存儲資源...");
        synchronized (this) {
            safeClose(orders);
            safeClose(trades);
            safeClose(balances);
            safeClose(userAssets);
            safeClose(activeOrders);
            safeClose(cids);
            safeClose(userActiveOrders);
            safeClose(msgMetadata);
            safeClose(walMetadata);
            safeClose(metricsHistory);
            if (gatewaySenderWal != null) gatewaySenderWal.close();
        }
    }

    private void safeClose(ChronicleMap<?, ?> map) {
        if (map != null && !map.isClosed()) map.close();
    }
}
