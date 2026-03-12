package open.vincentf13.service.spot.infra.chronicle;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import open.vincentf13.service.spot.model.*;

import java.io.File;
import java.io.IOException;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 系統存儲管理中心 (Storage Center)
 職責：統一管理與初始化全系統所需的 Chronicle Map (內存映射表) 與 Chronicle Queue (消息隊列)
 */
@Slf4j
public class Storage {
    private static final Storage INSTANCE = new Storage();
    public static Storage self() { return INSTANCE; }

    private String mapDir = ChronicleMapEnum.DEFAULT_BASE_DIR;
    private String walDir = ChronicleMapEnum.WAL_BASE_DIR;

    // --- Chronicle Maps (狀態存儲) ---
    private ChronicleMap<BalanceKey, Balance> balances;
    private ChronicleMap<Long, Long> userAssets;
    private ChronicleMap<Long, Order> orders;
    private ChronicleMap<Long, Boolean> activeOrders;
    private ChronicleMap<Long, String> userActiveOrders; // 新增：userId -> 逗號分隔的 orderIds
    private ChronicleMap<Long, Trade> trades;
    private ChronicleMap<CidKey, Long> cids;
    private ChronicleMap<Byte, Progress> metadata;

    // --- Chronicle Queues (數據流 WAL) ---
    private ChronicleQueue clientToGwWal;    // Client -> Gateway
    private ChronicleQueue gwToMatchingWal;  // Gateway -> Matching
    private ChronicleQueue matchingToGwWal;  // Matching -> Gateway

    private Storage() {
        init();
    }

    private void init() {
        try {
            ensureDir(mapDir);
            ensureDir(walDir);
            
            // 1. 初始化 Maps (加入檔案損壞防護機制)
            balances = buildMap(ChronicleMap.of(BalanceKey.class, Balance.class)
                    .name(ChronicleMapEnum.BALANCES)
                    .entries(100_000)
                    .averageValue(new Balance()), new File(mapDir + ChronicleMapEnum.BALANCES));

            userAssets = buildMap(ChronicleMap.of(Long.class, Long.class)
                    .name(ChronicleMapEnum.USER_ASSETS)
                    .entries(100_000), new File(mapDir + ChronicleMapEnum.USER_ASSETS));

            orders = buildMap(ChronicleMap.of(Long.class, Order.class)
                    .name(ChronicleMapEnum.ORDERS)
                    .entries(1_000_000)
                    .averageValue(new Order()), new File(mapDir + ChronicleMapEnum.ORDERS));

            activeOrders = buildMap(ChronicleMap.of(Long.class, Boolean.class)
                    .name(ChronicleMapEnum.ACTIVE_ORDERS)
                    .entries(100_000), new File(mapDir + ChronicleMapEnum.ACTIVE_ORDERS));

            userActiveOrders = buildMap(ChronicleMap.of(Long.class, String.class)
                    .name(ChronicleMapEnum.USER_ACTIVE_ORDERS)
                    .entries(100_000)
                    .averageValue("1234567890123456789,"), new File(mapDir + ChronicleMapEnum.USER_ACTIVE_ORDERS));

            trades = buildMap(ChronicleMap.of(Long.class, Trade.class)
                    .name(ChronicleMapEnum.TRADES)
                    .entries(10_000_000)
                    .averageValue(new Trade()), new File(mapDir + ChronicleMapEnum.TRADES));

            cids = buildMap(ChronicleMap.of(CidKey.class, Long.class)
                    .name(ChronicleMapEnum.CIDS)
                    .entries(1_000_000)
                    .averageValue(1L), new File(mapDir + ChronicleMapEnum.CIDS));

            metadata = buildMap(ChronicleMap.of(Byte.class, Progress.class)
                    .name(ChronicleMapEnum.METADATA)
                    .entries(100)
                    .averageValue(new Progress()), new File(mapDir + ChronicleMapEnum.METADATA));

            // 2. 初始化 Queues
            clientToGwWal = SingleChronicleQueueBuilder.binary(walDir + ChronicleQueueEnum.CLIENT_TO_GW.getPath()).build();
            gwToMatchingWal = SingleChronicleQueueBuilder.binary(walDir + ChronicleQueueEnum.GW_TO_MATCHING.getPath()).build();
            matchingToGwWal = SingleChronicleQueueBuilder.binary(walDir + ChronicleQueueEnum.MATCHING_TO_GW.getPath()).build();

            log.info("Chronicle Storage 初始化完成。Map 目錄: {}, WAL 目錄: {}", mapDir, walDir);
        } catch (Exception e) {
            log.error("Chronicle Storage 初始化致命錯誤！請檢查檔案是否損壞或權限問題: {}", e.getMessage(), e);
            throw new RuntimeException("Storage init failed", e);
        }
    }

    private <K, V> ChronicleMap<K, V> buildMap(net.openhft.chronicle.map.ChronicleMapBuilder<K, V> builder, File file) throws IOException {
        try {
            return builder.createPersistedTo(file);
        } catch (IllegalStateException e) {
            log.error("檢測到 ChronicleMap 檔案可能損壞或配置不兼容: {}。請考慮清理該檔案進行災難恢復。", file.getAbsolutePath(), e);
            throw e;
        }
    }

    private void ensureDir(String path) {
        File dir = new File(path);
        if (!dir.exists()) dir.mkdirs();
    }

    // --- Getters ---
    public ChronicleMap<BalanceKey, Balance> balances() { return balances; }
    public ChronicleMap<Long, Long> userAssets() { return userAssets; }
    public ChronicleMap<Long, Order> orders() { return orders; }
    public ChronicleMap<Long, Boolean> activeOrders() { return activeOrders; }
    public ChronicleMap<Long, String> userActiveOrders() { return userActiveOrders; }
    public ChronicleMap<Long, Trade> trades() { return trades; }
    public ChronicleMap<CidKey, Long> cids() { return cids; }
    public ChronicleMap<Byte, Progress> metadata() { return metadata; }

    public ChronicleQueue clientToGwWal() { return clientToGwWal; }
    public ChronicleQueue gwToMatchingWal() { return gwToMatchingWal; }
    public ChronicleQueue matchingToGwWal() { return matchingToGwWal; }

    public void close() {
        if (balances != null) balances.close();
        if (userAssets != null) userAssets.close();
        if (orders != null) orders.close();
        if (activeOrders != null) activeOrders.close();
        if (userActiveOrders != null) userActiveOrders.close();
        if (trades != null) trades.close();
        if (cids != null) cids.close();
        if (metadata != null) metadata.close();
        
        if (clientToGwWal != null) clientToGwWal.close();
        if (gwToMatchingWal != null) gwToMatchingWal.close();
        if (matchingToGwWal != null) matchingToGwWal.close();
    }
}
