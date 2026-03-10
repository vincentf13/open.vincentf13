package open.vincentf13.service.spot.infra.chronicle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import open.vincentf13.service.spot.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/**
  系統存儲中心 (Storage)
  管理所有基於 Memory-mapped Files 的 Chronicle Map (狀態) 與 Chronicle Queue (流轉)
  此組件透過單例模式 self() 提供全系統共享訪問
 */
@Slf4j
@Component
public class Storage {
    private static Storage instance;
    public static Storage self() { return instance; }

    @Value("${state.base-dir:data/spot-exchange/}")
    private String baseDir;

    @Value("${state.entries.orders:1000000}")
    private int orderEntries;

    @Value("${state.entries.balances:100000}")
    private int balanceEntries;

    // --- 系統狀態 (Maps) ---
    private ChronicleMap<BalanceKey, Balance> balances;
    private ChronicleMap<Long, Long> userAssets; // 用於快速定位用戶擁有的非零資產 (Bitmask)
    private ChronicleMap<Long, Order> orders;
    private ChronicleMap<Long, Boolean> activeOrders; // 掛單索引，用於啟動時快速重建訂單簿
    private ChronicleMap<Long, Trade> trades;
    private ChronicleMap<CidKey, Long> cids; // 冪等性校驗 Map
    private ChronicleMap<Byte, Progress> metadata; // 存儲各組件的 Sequence ID 與全局計數器
    
    // --- 數據隊列 (Queues) ---
    private ChronicleQueue gatewayQueue;  // 網關接收指令緩衝
    private ChronicleQueue commandQueue;  // 撮合引擎待處理指令 (WAL)
    private ChronicleQueue resultQueue;   // 撮合引擎執行結果回報 (用於推播與外部訂閱)

    @PostConstruct
    public void init() throws IOException {
        if (!baseDir.endsWith("/")) baseDir += "/";
        new File(baseDir).mkdirs();

        balances = createMap("balances", BalanceKey.class, Balance.class, balanceEntries, new BalanceKey(), new Balance());
        userAssets = createMap("user-assets", Long.class, Long.class, balanceEntries, 0L, 0L);
        orders = createMap("orders", Long.class, Order.class, orderEntries, 0L, new Order());
        activeOrders = createMap("active-idx", Long.class, Boolean.class, orderEntries, 0L, true);
        trades = createMap("trades", Long.class, Trade.class, orderEntries, 0L, new Trade());
        cids = createMap("cid-idx", CidKey.class, Long.class, orderEntries, new CidKey(), 0L);
        metadata = createMap("metadata", Byte.class, Progress.class, 100, (byte)0, new Progress());

        gatewayQueue = SingleChronicleQueueBuilder.binary(baseDir + "gw-queue").build();
        commandQueue = SingleChronicleQueueBuilder.binary(baseDir + "core-queue").build();
        resultQueue = SingleChronicleQueueBuilder.binary(baseDir + "outbound-queue").build();
        
        instance = this;
        log.info("Chronicle Storage initialized and static instance set.");
    }

    private <K, V> ChronicleMap<K, V> createMap(String name, Class<K> k, Class<V> v, int entries, K avgKey, V avgValue) throws IOException {
        return ChronicleMap.of(k, v).name(name).entries(entries).averageKey(avgKey).averageValue(avgValue).createPersistedTo(new File(baseDir + name + ".dat"));
    }

    public ChronicleMap<BalanceKey, Balance> balances() { return balances; }
    public ChronicleMap<Long, Long> userAssets() { return userAssets; }
    public ChronicleMap<Long, Order> orders() { return orders; }
    public ChronicleMap<Long, Boolean> activeOrders() { return activeOrders; }
    public ChronicleMap<Long, Trade> trades() { return trades; }
    public ChronicleMap<CidKey, Long> cids() { return cids; }
    public ChronicleMap<Byte, Progress> metadata() { return metadata; }
    
    public ChronicleQueue gatewayQueue() { return gatewayQueue; }
    public ChronicleQueue commandQueue() { return commandQueue; }
    public ChronicleQueue resultQueue() { return resultQueue; }

    @PreDestroy
    public void close() {
        closeQuietly(balances); closeQuietly(userAssets); closeQuietly(orders);
        closeQuietly(activeOrders); closeQuietly(trades); closeQuietly(cids); closeQuietly(metadata);
        closeQuietly(gatewayQueue); closeQuietly(commandQueue); closeQuietly(resultQueue);
    }

    private void closeQuietly(AutoCloseable c) { try { if (c != null) c.close(); } catch (Exception ignored) {} }
}
