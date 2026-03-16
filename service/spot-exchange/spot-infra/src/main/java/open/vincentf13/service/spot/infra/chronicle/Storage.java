package open.vincentf13.service.spot.infra.chronicle;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import open.vincentf13.service.spot.infra.Constants.ChronicleMapEnum;
import open.vincentf13.service.spot.infra.Constants.ChronicleQueueEnum;
import open.vincentf13.service.spot.infra.Constants.Ws;
import open.vincentf13.service.spot.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 系統存儲中心 (Storage Hub) - TPS 測試簡化版
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

    // --- 內存 Queues (Inter-thread) ---
    private volatile ManyToOneRingBuffer gatewayWalQueue;
    private volatile ManyToOneRingBuffer engineWorkQueue;

    public ManyToOneRingBuffer gatewayWalQueue() {
        if (gatewayWalQueue == null) synchronized (this) {
            if (gatewayWalQueue == null) {
                int bufferSize = Ws.WAL_RING_BUFFER_SIZE;
                int totalSize = bufferSize + RingBufferDescriptor.TRAILER_LENGTH;
                ByteBuffer byteBuffer = ByteBuffer.allocateDirect(totalSize);
                gatewayWalQueue = new ManyToOneRingBuffer(new UnsafeBuffer(byteBuffer));
                log.info("Gateway WAL RingBuffer initialized: size={}MB", bufferSize / 1024 / 1024);
            }
        }
        return gatewayWalQueue;
    }

    public ManyToOneRingBuffer engineWorkQueue() {
        if (engineWorkQueue == null) synchronized (this) {
            if (engineWorkQueue == null) {
                // 撮合工作隊列建議大小 16MB (足夠緩衝突發流量)
                int bufferSize = 16 * 1024 * 1024;
                int totalSize = bufferSize + RingBufferDescriptor.TRAILER_LENGTH;
                ByteBuffer byteBuffer = ByteBuffer.allocateDirect(totalSize);
                engineWorkQueue = new ManyToOneRingBuffer(new UnsafeBuffer(byteBuffer));
                log.info("Engine Work RingBuffer initialized: size={}MB", bufferSize / 1024 / 1024);
            }
        }
        return engineWorkQueue;
    }

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

    // CPU ID 監測指標 (執行緒綁核追蹤)
    public static final long KEY_CPU_ID_ENGINE = -100L;
    public static final long KEY_CPU_ID_WAL_WRITER = -101L;
    public static final long KEY_CPU_ID_AERON_SENDER = -102L;
    public static final long KEY_CPU_ID_AERON_RECEIVER = -103L;
    public static final long KEY_CPU_ID_JVM_MANAGEMENT = -105L;
    public static final long KEY_CPU_ID_NETTY_BOSS = -106L;
    public static final long KEY_CPU_ID_NETTY_WORKER_1 = -107L;
    public static final long KEY_CPU_ID_NETTY_WORKER_2 = -108L;
    public static final long KEY_CPU_ID_AERON_CONDUCTOR = -109L;

    // 雙端 JVM 與 CPU 指標 (用於跨進程彙整)
    public static final long KEY_MATCHING_JVM_USED_MB = -200L;
    public static final long KEY_MATCHING_JVM_MAX_MB = -201L;
    public static final long KEY_MATCHING_CPU_LOAD = -202L;
    
    public static final long KEY_GATEWAY_JVM_USED_MB = -210L;
    public static final long KEY_GATEWAY_JVM_MAX_MB = -211L;
    public static final long KEY_GATEWAY_CPU_LOAD = -212L;

    public ChronicleMap<Long, Long> metricsHistory() {
        if (metricsHistory == null) synchronized (this) {
            if (metricsHistory == null) metricsHistory = createMap("metrics-history", Long.class, Long.class, 86400, 8, 8);
        }
        return metricsHistory;
    }

    public ChronicleQueue gatewaySenderWal() {
        if (gatewaySenderWal == null) synchronized (this) {
            if (gatewaySenderWal == null) gatewaySenderWal = createQueue(ChronicleQueueEnum.CLIENT_TO_GW);
        }
        return gatewaySenderWal;
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
        return net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder
                .single(dir + q.getPath())
                .syncMode(net.openhft.chronicle.bytes.SyncMode.SYNC)
                .build();
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
        }
    }
}
