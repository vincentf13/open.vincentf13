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
 * 系統存儲中心 (Storage Hub) - 懶加載版
 * 管理所有 Chronicle Map 與 Chronicle Queue 實例的生命週期
 */
@Slf4j
public class Storage {
    private static final Storage INSTANCE = new Storage();
    public static Storage self() { return INSTANCE; }

    private Storage() {}

    // --- Chronicle Map 實例 (Lazy) ---
    private volatile ChronicleMap<Long, Order> orders;
    private volatile ChronicleMap<Long, Trade> trades;
    private volatile ChronicleMap<BalanceKey, Balance> balances;
    private volatile ChronicleMap<Long, Long> userAssets;
    private volatile ChronicleMap<Long, Boolean> activeOrders;
    private volatile ChronicleMap<Long, byte[]> userActiveOrders;
    private volatile ChronicleMap<CidKey, Long> cids;
    private volatile ChronicleMap<Byte, MsgProgress> msgMetadata;
    private volatile ChronicleMap<Byte, WalProgress> walMetadata;

    // --- Chronicle Queue 實例 (Lazy) ---
    private volatile ChronicleQueue gatewaySenderWal;
    private volatile ChronicleQueue engineReceiverWal;
    private volatile ChronicleQueue engineSenderWal;
    private volatile ChronicleQueue gatewayReceiverWal;

    // --- Map Getters with Lazy Init ---

    public ChronicleMap<Long, Order> orders() {
        if (orders == null) {
            synchronized (this) {
                if (orders == null) orders = createMap(ChronicleMapEnum.ORDERS, Long.class, Order.class, 1_000_000, 128);
            }
        }
        return orders;
    }

    public ChronicleMap<Long, Trade> trades() {
        if (trades == null) {
            synchronized (this) {
                if (trades == null) trades = createMap(ChronicleMapEnum.TRADES, Long.class, Trade.class, 1_000_000, 64);
            }
        }
        return trades;
    }

    public ChronicleMap<BalanceKey, Balance> balances() {
        if (balances == null) {
            synchronized (this) {
                if (balances == null) balances = createMap(ChronicleMapEnum.BALANCES, BalanceKey.class, Balance.class, 1_000_000, 16, 64);
            }
        }
        return balances;
    }

    public ChronicleMap<Long, Long> userAssets() {
        if (userAssets == null) {
            synchronized (this) {
                if (userAssets == null) userAssets = createMap(ChronicleMapEnum.USER_ASSETS, Long.class, Long.class, 100_000, 0);
            }
        }
        return userAssets;
    }

    public ChronicleMap<Long, Boolean> activeOrders() {
        if (activeOrders == null) {
            synchronized (this) {
                if (activeOrders == null) activeOrders = createMap(ChronicleMapEnum.ACTIVE_ORDERS, Long.class, Boolean.class, 1_000_000, 0);
            }
        }
        return activeOrders;
    }

    public ChronicleMap<Long, byte[]> userActiveOrders() {
        if (userActiveOrders == null) {
            synchronized (this) {
                if (userActiveOrders == null) userActiveOrders = createMap(ChronicleMapEnum.USER_ACTIVE_ORDERS, Long.class, byte[].class, 100_000, 256);
            }
        }
        return userActiveOrders;
    }

    public ChronicleMap<CidKey, Long> cids() {
        if (cids == null) {
            synchronized (this) {
                if (cids == null) cids = createMap(ChronicleMapEnum.CIDS, CidKey.class, Long.class, 1_000_000, 16, 0);
            }
        }
        return cids;
    }

    public ChronicleMap<CidKey, Long> clientOrderIdMap() { return cids(); }

    public ChronicleMap<Byte, MsgProgress> msgMetadata() {
        if (msgMetadata == null) {
            synchronized (this) {
                if (msgMetadata == null) msgMetadata = createMap("msg-" + ChronicleMapEnum.METADATA, Byte.class, MsgProgress.class, 10, 0);
            }
        }
        return msgMetadata;
    }

    public ChronicleMap<Byte, WalProgress> walMetadata() {
        if (walMetadata == null) {
            synchronized (this) {
                if (walMetadata == null) walMetadata = createMap("wal-" + ChronicleMapEnum.METADATA, Byte.class, WalProgress.class, 10, 0);
            }
        }
        return walMetadata;
    }

    // --- Queue Getters with Lazy Init ---

    public ChronicleQueue gatewaySenderWal() {
        if (gatewaySenderWal == null) {
            synchronized (this) {
                if (gatewaySenderWal == null) gatewaySenderWal = createQueue(ChronicleQueueEnum.CLIENT_TO_GW);
            }
        }
        return gatewaySenderWal;
    }

    public ChronicleQueue engineReceiverWal() {
        if (engineReceiverWal == null) {
            synchronized (this) {
                if (engineReceiverWal == null) engineReceiverWal = createQueue(ChronicleQueueEnum.GW_TO_MATCHING);
            }
        }
        return engineReceiverWal;
    }

    public ChronicleQueue engineSenderWal() {
        if (engineSenderWal == null) {
            synchronized (this) {
                if (engineSenderWal == null) engineSenderWal = createQueue(ChronicleQueueEnum.MATCHING_TO_ENGINE_SENDER);
            }
        }
        return engineSenderWal;
    }

    public ChronicleQueue gatewayReceiverWal() {
        if (gatewayReceiverWal == null) {
            synchronized (this) {
                if (gatewayReceiverWal == null) gatewayReceiverWal = createQueue(ChronicleQueueEnum.ENGINE_TO_GATEWAY_RECEIVER);
            }
        }
        return gatewayReceiverWal;
    }

    // --- Private Helpers ---

    private <K, V> ChronicleMap<K, V> createMap(String name, Class<K> keyCls, Class<V> valCls, int entries, int valSize) {
        return createMap(name, keyCls, valCls, entries, 0, valSize);
    }

    private <K, V> ChronicleMap<K, V> createMap(String name, Class<K> keyCls, Class<V> valCls, int entries, int keySize, int valSize) {
        try {
            String base = ChronicleMapEnum.DEFAULT_BASE_DIR;
            new File(base).mkdirs();
            var builder = ChronicleMap.of(keyCls, valCls).name(name).entries(entries);
            if (keySize > 0) builder.averageKeySize(keySize);
            if (valSize > 0) builder.averageValueSize(valSize);
            log.info("初始化 Chronicle Map: {}", name);
            return builder.createPersistedTo(new File(base + name));
        } catch (IOException e) {
            throw new RuntimeException("無法建立 Chronicle Map: " + name, e);
        }
    }

    private ChronicleQueue createQueue(ChronicleQueueEnum qEnum) {
        String base = ChronicleMapEnum.WAL_BASE_DIR;
        new File(base).mkdirs();
        log.info("初始化 Chronicle Queue: {}", qEnum.getPath());
        return ChronicleQueue.single(base + qEnum.getPath());
    }

    public void close() {
        synchronized (this) {
            if (orders != null) { orders.close(); orders = null; }
            if (trades != null) { trades.close(); trades = null; }
            if (balances != null) { balances.close(); balances = null; }
            if (userAssets != null) { userAssets.close(); userAssets = null; }
            if (activeOrders != null) { activeOrders.close(); activeOrders = null; }
            if (userActiveOrders != null) { userActiveOrders.close(); userActiveOrders = null; }
            if (cids != null) { cids.close(); cids = null; }
            if (msgMetadata != null) { msgMetadata.close(); msgMetadata = null; }
            if (walMetadata != null) { walMetadata.close(); walMetadata = null; }
            
            if (gatewaySenderWal != null) { gatewaySenderWal.close(); gatewaySenderWal = null; }
            if (engineReceiverWal != null) { engineReceiverWal.close(); engineReceiverWal = null; }
            if (engineSenderWal != null) { engineSenderWal.close(); engineSenderWal = null; }
            if (gatewayReceiverWal != null) { gatewayReceiverWal.close(); gatewayReceiverWal = null; }
        }
    }
}
