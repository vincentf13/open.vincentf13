package open.vincentf13.service.spot.infra.chronicle;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
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
 * 系統存儲中心 (Storage Hub) - 高性能優化版
 * 職責：統一管理所有持久化 (Chronicle Map/Queue) 與內存 (RingBuffer) 資源
 */
@Slf4j
public class Storage {
    private static final Storage INSTANCE = new Storage();
    public static Storage self() { return INSTANCE; }

    // --- 持久化 Maps (Final & Pre-initialized) ---
    private final ChronicleMap<Long, Order> orders;
    private final ChronicleMap<Long, Trade> trades;
    private final ChronicleMap<BalanceKey, Balance> balances;
    private final ChronicleMap<Long, Long> userAssets;
    private final ChronicleMap<Long, Boolean> activeOrders;
    private final ChronicleMap<CidKey, Long> cids;
    private final ChronicleMap<Long, byte[]> userActiveOrders;
    private final ChronicleMap<Byte, MsgProgress> msgMetadata;
    private final ChronicleMap<Byte, WalProgress> walMetadata;
    private final ChronicleMap<Long, Long> metricsHistory;

    // --- 持久化 Queues (WAL) ---
    private final ChronicleQueue gatewaySenderWal;

    // --- 內存 Queues (Lazy Init via Double-Check) ---
    private volatile ManyToOneRingBuffer gatewayWalQueue;
    private volatile ManyToOneRingBuffer engineWorkQueue;

    private Storage() {
        log.info(">>> [INIT] 正在初始化 Chronicle 存儲資源...");
        this.orders = createMap(ChronicleMapEnum.ORDERS, Long.class, Order.class, 10_000_000, 128);
        this.trades = createMap(ChronicleMapEnum.TRADES, Long.class, Trade.class, 10_000_000, 64);
        this.balances = createMap(ChronicleMapEnum.BALANCES, BalanceKey.class, Balance.class, 10_000_000, 16, 64);
        this.userAssets = createMap(ChronicleMapEnum.USER_ASSETS, Long.class, Long.class, 1_000_000, 8);
        this.activeOrders = createMap(ChronicleMapEnum.ACTIVE_ORDERS, Long.class, Boolean.class, 10_000_000, 1);
        this.cids = createMap(ChronicleMapEnum.CIDS, CidKey.class, Long.class, 10_000_000, 16, 0);
        this.userActiveOrders = createMap(ChronicleMapEnum.USER_ACTIVE_ORDERS, Long.class, byte[].class, 100_000, 256);
        this.msgMetadata = createMap("msg-" + ChronicleMapEnum.METADATA, Byte.class, MsgProgress.class, 10, 0);
        this.walMetadata = createMap("wal-" + ChronicleMapEnum.METADATA, Byte.class, WalProgress.class, 10, 0);
        this.metricsHistory = createMap(ChronicleMapEnum.METRICS_HISTORY, Long.class, Long.class, 86400 * 2, 8, 8); // 保留 2 天指標
        
        this.gatewaySenderWal = createQueue(ChronicleQueueEnum.CLIENT_TO_GW);
        log.info(">>> [INIT] Chronicle 存儲資源初始化完成。");
    }

    // --- Getters (Thread-Safe by Final) ---
    public ChronicleMap<Long, Order> orders() { return orders; }
    public ChronicleMap<Long, Trade> trades() { return trades; }
    public ChronicleMap<BalanceKey, Balance> balances() { return balances; }
    public ChronicleMap<Long, Long> userAssets() { return userAssets; }
    public ChronicleMap<Long, Boolean> activeOrders() { return activeOrders; }
    public ChronicleMap<CidKey, Long> clientOrderIdMap() { return cids; }
    public ChronicleMap<Long, byte[]> userActiveOrders() { return userActiveOrders; }
    public ChronicleMap<Byte, MsgProgress> msgProgressMetadata() { return msgMetadata; }
    public ChronicleMap<Byte, WalProgress> walMetadata() { return walMetadata; }
    public ChronicleMap<Long, Long> metricsHistory() { return metricsHistory; }
    public ChronicleQueue gatewaySenderWal() { return gatewaySenderWal; }

    // --- RingBuffer Getters (Lazy) ---

    public ManyToOneRingBuffer gatewayWalQueue() {
        if (gatewayWalQueue == null) synchronized (this) {
            if (gatewayWalQueue == null) {
                gatewayWalQueue = initRingBuffer("GatewayWAL", Ws.WAL_RING_BUFFER_SIZE);
            }
        }
        return gatewayWalQueue;
    }

    public ManyToOneRingBuffer engineWorkQueue() {
        if (engineWorkQueue == null) synchronized (this) {
            if (engineWorkQueue == null) {
                engineWorkQueue = initRingBuffer("EngineWork", 64 * 1024 * 1024);
            }
        }
        return engineWorkQueue;
    }

    private ManyToOneRingBuffer initRingBuffer(String name, int bufferSize) {
        int totalSize = bufferSize + RingBufferDescriptor.TRAILER_LENGTH;
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(totalSize);
        log.info("{} RingBuffer initialized: size={}MB", name, bufferSize / 1024 / 1024);
        return new ManyToOneRingBuffer(new UnsafeBuffer(byteBuffer));
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
                .indexCount(1024)   // 增加至 1024，支援更大規模數據
                .indexSpacing(1024) // 增加至 1024，確保總索引量充足
                .syncMode(net.openhft.chronicle.bytes.SyncMode.SYNC) // 強制同步模式
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
