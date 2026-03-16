package open.vincentf13.service.spot.infra.chronicle;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import open.vincentf13.service.spot.infra.Constants.ChronicleMapEnum;
import open.vincentf13.service.spot.infra.Constants.ChronicleQueueEnum;
import open.vincentf13.service.spot.model.*;

import java.io.File;
import java.io.IOException;

/**
 * 系統存儲中心 (Storage Hub) - TPS 測試簡化版
 * 保留所有業務所需的 Maps，僅移除回報鏈路的 Queues 以提升性能。
 */
@Slf4j
public class Storage {
    private static final Storage INSTANCE = new Storage();
    public static Storage self() { return INSTANCE; }

    private Storage() {}

    // --- 持久化 Maps (Lazy Init) ---
    private volatile ChronicleMap<Long, Order> orders;
    private volatile ChronicleMap<Long, Trade> trades;
    private volatile ChronicleMap<BalanceKey, Balance> balances;
    private volatile ChronicleMap<Long, Long> userAssets;
    private volatile ChronicleMap<Long, Boolean> activeOrders;
    private volatile ChronicleMap<CidKey, Long> cids;
    private volatile ChronicleMap<Long, byte[]> userActiveOrders;
    private volatile ChronicleMap<Byte, MsgProgress> msgMetadata;
    private volatile ChronicleMap<Byte, WalProgress> walMetadata;
    private volatile ChronicleMap<Long, Long> metricsHistory;

    // --- 持久化 Queues (WAL) ---
    private volatile ChronicleQueue gatewaySenderWal;    // 網關 -> Aeron
    private volatile ChronicleQueue engineReceiverWal;   // Aeron -> 引擎

    public ChronicleMap<Long, Order> orders() {
        if (orders == null) synchronized (this) {
            if (orders == null) orders = createMap(ChronicleMapEnum.ORDERS, Long.class, Order.class, 10_000_000, 128);
        }
        return orders;
    }

    public ChronicleMap<Long, Trade> trades() {
        if (trades == null) synchronized (this) {
            if (trades == null) trades = createMap(ChronicleMapEnum.TRADES, Long.class, Trade.class, 10_000_000, 64);
        }
        return trades;
    }

    public ChronicleMap<BalanceKey, Balance> balances() {
        if (balances == null) synchronized (this) {
            if (balances == null) balances = createMap(ChronicleMapEnum.BALANCES, BalanceKey.class, Balance.class, 10_000_000, 16, 64);
        }
        return balances;
    }

    public ChronicleMap<Long, Long> userAssets() {
        if (userAssets == null) synchronized (this) {
            if (userAssets == null) userAssets = createMap(ChronicleMapEnum.USER_ASSETS, Long.class, Long.class, 1_000_000, 8);
        }
        return userAssets;
    }

    public ChronicleMap<Long, Boolean> activeOrders() {
        if (activeOrders == null) synchronized (this) {
            if (activeOrders == null) activeOrders = createMap(ChronicleMapEnum.ACTIVE_ORDERS, Long.class, Boolean.class, 10_000_000, 1);
        }
        return activeOrders;
    }

    public ChronicleMap<CidKey, Long> clientOrderIdMap() {
        if (cids == null) synchronized (this) {
            if (cids == null) cids = createMap(ChronicleMapEnum.CIDS, CidKey.class, Long.class, 10_000_000, 16, 0);
        }
        return cids;
    }

    public ChronicleMap<Long, byte[]> userActiveOrders() {
        if (userActiveOrders == null) synchronized (this) {
            if (userActiveOrders == null) userActiveOrders = createMap(ChronicleMapEnum.USER_ACTIVE_ORDERS, Long.class, byte[].class, 100_000, 256);
        }
        return userActiveOrders;
    }

    public ChronicleMap<Byte, MsgProgress> msgProgressMetadata() {
        if (msgMetadata == null) synchronized (this) {
            if (msgMetadata == null) msgMetadata = createMap("msg-" + ChronicleMapEnum.METADATA, Byte.class, MsgProgress.class, 10, 0);
        }
        return msgMetadata;
    }

    public ChronicleMap<Byte, WalProgress> walMetadata() {
        if (walMetadata == null) synchronized (this) {
            if (walMetadata == null) walMetadata = createMap("wal-" + ChronicleMapEnum.METADATA, Byte.class, WalProgress.class, 10, 0);
        }
        return walMetadata;
    }

    public static final long KEY_POLL_COUNT = -1L;
    public static final long KEY_WORK_COUNT = -2L;
    public static final long KEY_NETTY_RECV_COUNT = -3L;
    public static final long KEY_AERON_BACKPRESSURE = -4L;
    public static final long KEY_GATEWAY_WAL_WRITE_COUNT = -5L;
    public static final long KEY_AERON_SEND_COUNT = -6L;
    public static final long KEY_AERON_RECV_COUNT = -7L;

    public ChronicleMap<Long, Long> metricsHistory() {
        if (metricsHistory == null) synchronized (this) {
            if (metricsHistory == null) metricsHistory = createMap("metrics-history", Long.class, Long.class, 86400, 8, 8);
        }
        return metricsHistory;
    }

    // --- WAL Queues ---

    public ChronicleQueue gatewaySenderWal() {
        if (gatewaySenderWal == null) synchronized (this) {
            if (gatewaySenderWal == null) gatewaySenderWal = createQueue(ChronicleQueueEnum.CLIENT_TO_GW);
        }
        return gatewaySenderWal;
    }

    public ChronicleQueue engineReceiverWal() {
        if (engineReceiverWal == null) synchronized (this) {
            if (engineReceiverWal == null) engineReceiverWal = createQueue(ChronicleQueueEnum.GW_TO_MATCHING);
        }
        return engineReceiverWal;
    }

    // --- Helpers ---

    private <K, V> ChronicleMap<K, V> createMap(Object name, Class<K> keyCls, Class<V> valCls, int entries, int valSize) {
        return createMap(name.toString(), keyCls, valCls, entries, 0, valSize);
    }

    private <K, V> ChronicleMap<K, V> createMap(Object name, Class<K> keyCls, Class<V> valCls, int entries, int keySize, int valSize) {
        try {
            String dir = ChronicleMapEnum.DEFAULT_BASE_DIR;
            new File(dir).mkdirs();
            var builder = ChronicleMap.of(keyCls, valCls).name(name.toString()).entries(entries);
            
            // 針對新版 Chronicle Map 的嚴格檢查：
            // 嘗試設定 Key 大小，如果該類別是靜態已知大小 (如 Long, Boolean)，Chronicle 會噴 IllegalStateException，我們直接忽略。
            try {
                if (keySize > 0) builder.averageKeySize(keySize);
                else if (keyCls == String.class) builder.averageKeySize(32);
                else builder.averageKeySize(16);
            } catch (IllegalStateException e) {
                // Skip: Size is statically known
            }

            try {
                if (valSize > 0) builder.averageValueSize(valSize);
                else if (valCls.isInterface()) builder.averageValueSize(32);
                else if (valCls == byte[].class) builder.averageValueSize(256);
                else builder.averageValueSize(64);
            } catch (IllegalStateException e) {
                // Skip: Size is statically known
            }
            
            return builder.createPersistedTo(new File(dir + name));
        } catch (IOException e) {
            throw new RuntimeException("ChronicleMap 建立失敗: " + name, e);
        }
    }

    // 移除不再需要的輔助方法

    // 移除不再需要的 isStaticSize 輔助方法

    private ChronicleQueue createQueue(ChronicleQueueEnum q) {
        String dir = ChronicleMapEnum.WAL_BASE_DIR;
        new File(dir).mkdirs();
        return ChronicleQueue.single(dir + q.getPath());
    }

    public void close() {
        synchronized (this) {
            if (orders != null) { orders.close(); orders = null; }
            if (trades != null) { trades.close(); trades = null; }
            if (balances != null) { balances.close(); balances = null; }
            if (userAssets != null) { userAssets.close(); userAssets = null; }
            if (activeOrders != null) { activeOrders.close(); activeOrders = null; }
            if (cids != null) { cids.close(); cids = null; }
            if (userActiveOrders != null) { userActiveOrders.close(); userActiveOrders = null; }
            if (msgMetadata != null) { msgMetadata.close(); msgMetadata = null; }
            if (walMetadata != null) { walMetadata.close(); walMetadata = null; }
            if (metricsHistory != null) { metricsHistory.close(); metricsHistory = null; }
            if (gatewaySenderWal != null) { gatewaySenderWal.close(); gatewaySenderWal = null; }
            if (engineReceiverWal != null) { engineReceiverWal.close(); engineReceiverWal = null; }
        }
    }
}
