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
 * 系統存儲中心 (Storage Hub)
 * 管理所有 Chronicle Map 與 Chronicle Queue 實例的生命週期
 */
@Slf4j
public class Storage {
    private static final Storage INSTANCE = new Storage();

    public static Storage self() { return INSTANCE; }

    // --- Chronicle Map 實例 ---
    private ChronicleMap<Long, Order> orders;
    private ChronicleMap<Long, Trade> trades;
    private ChronicleMap<BalanceKey, Balance> balances;
    private ChronicleMap<Long, Long> userAssets; // 用戶資產位元遮罩索引
    private ChronicleMap<Long, Boolean> activeOrders; // 活躍訂單
    private ChronicleMap<Long, String> userActiveOrders; // 用戶活躍訂單 (ID 列表字串)
    private ChronicleMap<CidKey, Long> cids;
    private ChronicleMap<Byte, MsgProgress> msgMetadata;
    private ChronicleMap<Byte, WalProgress> walMetadata;

    // --- Chronicle Queue 實例 ---
    private ChronicleQueue gatewaySenderWal;
    private ChronicleQueue engineReceiverWal;
    private ChronicleQueue engineSenderWal;
    private ChronicleQueue gatewayReceiverWal;

    private Storage() {
        try {
            initMaps();
            initQueues();
        } catch (IOException e) {
            log.error("Chronicle Storage 初始化失敗: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void initMaps() throws IOException {
        String base = ChronicleMapEnum.DEFAULT_BASE_DIR;
        new File(base).mkdirs();

        orders = ChronicleMap.of(Long.class, Order.class)
                .name(ChronicleMapEnum.ORDERS)
                .entries(1_000_000)
                .averageValueSize(128)
                .createPersistedTo(new File(base + ChronicleMapEnum.ORDERS));

        trades = ChronicleMap.of(Long.class, Trade.class)
                .name(ChronicleMapEnum.TRADES)
                .entries(1_000_000)
                .averageValueSize(64)
                .createPersistedTo(new File(base + ChronicleMapEnum.TRADES));

        balances = ChronicleMap.of(BalanceKey.class, Balance.class)
                .name(ChronicleMapEnum.BALANCES)
                .entries(1_000_000)
                .averageKeySize(16)
                .averageValueSize(64)
                .createPersistedTo(new File(base + ChronicleMapEnum.BALANCES));

        userAssets = ChronicleMap.of(Long.class, Long.class)
                .name(ChronicleMapEnum.USER_ASSETS)
                .entries(100_000)
                .createPersistedTo(new File(base + ChronicleMapEnum.USER_ASSETS));

        activeOrders = ChronicleMap.of(Long.class, Boolean.class)
                .name(ChronicleMapEnum.ACTIVE_ORDERS)
                .entries(1_000_000)
                .createPersistedTo(new File(base + ChronicleMapEnum.ACTIVE_ORDERS));

        userActiveOrders = ChronicleMap.of(Long.class, String.class)
                .name(ChronicleMapEnum.USER_ACTIVE_ORDERS)
                .entries(100_000)
                .averageValueSize(256)
                .createPersistedTo(new File(base + ChronicleMapEnum.USER_ACTIVE_ORDERS));

        cids = ChronicleMap.of(CidKey.class, Long.class)
                .name(ChronicleMapEnum.CIDS)
                .entries(1_000_000)
                .averageKeySize(16)
                .createPersistedTo(new File(base + ChronicleMapEnum.CIDS));

        msgMetadata = ChronicleMap.of(Byte.class, MsgProgress.class)
                .name(ChronicleMapEnum.METADATA)
                .entries(10)
                .createPersistedTo(new File(base + "msg-" + ChronicleMapEnum.METADATA));

        walMetadata = ChronicleMap.of(Byte.class, WalProgress.class)
                .name(ChronicleMapEnum.METADATA)
                .entries(10)
                .createPersistedTo(new File(base + "wal-" + ChronicleMapEnum.METADATA));
    }

    private void initQueues() {
        String base = ChronicleMapEnum.WAL_BASE_DIR;
        new File(base).mkdirs();

        gatewaySenderWal = ChronicleQueue.single(base + ChronicleQueueEnum.CLIENT_TO_GW.getPath());
        engineReceiverWal = ChronicleQueue.single(base + ChronicleQueueEnum.GW_TO_MATCHING.getPath());
        engineSenderWal = ChronicleQueue.single(base + ChronicleQueueEnum.MATCHING_TO_ENGINE_SENDER.getPath());
        gatewayReceiverWal = ChronicleQueue.single(base + ChronicleQueueEnum.ENGINE_TO_GATEWAY_RECEIVER.getPath());
    }

    // --- Getters ---
    public ChronicleMap<Long, Order> orders() { return orders; }
    public ChronicleMap<Long, Trade> trades() { return trades; }
    public ChronicleMap<BalanceKey, Balance> balances() { return balances; }
    public ChronicleMap<Long, Long> userAssets() { return userAssets; }
    public ChronicleMap<Long, Boolean> activeOrders() { return activeOrders; }
    public ChronicleMap<Long, String> userActiveOrders() { return userActiveOrders; }
    public ChronicleMap<CidKey, Long> cids() { return cids; }
    public ChronicleMap<CidKey, Long> clientOrderIdMap() { return cids; }
    public ChronicleMap<Byte, MsgProgress> msgMetadata() { return msgMetadata; }
    public ChronicleMap<Byte, WalProgress> walMetadata() { return walMetadata; }

    public ChronicleQueue gatewaySenderWal() { return gatewaySenderWal; }
    public ChronicleQueue engineReceiverWal() { return engineReceiverWal; }
    public ChronicleQueue engineSenderWal() { return engineSenderWal; }
    public ChronicleQueue gatewayReceiverWal() { return gatewayReceiverWal; }

    public void close() {
        if (orders != null) orders.close();
        if (trades != null) trades.close();
        if (balances != null) balances.close();
        if (userAssets != null) userAssets.close();
        if (activeOrders != null) activeOrders.close();
        if (userActiveOrders != null) userActiveOrders.close();
        if (cids != null) cids.close();
        if (msgMetadata != null) msgMetadata.close();
        if (walMetadata != null) walMetadata.close();
        
        if (gatewaySenderWal != null) gatewaySenderWal.close();
        if (engineReceiverWal != null) engineReceiverWal.close();
        if (engineSenderWal != null) engineSenderWal.close();
        if (gatewayReceiverWal != null) gatewayReceiverWal.close();
    }
}
