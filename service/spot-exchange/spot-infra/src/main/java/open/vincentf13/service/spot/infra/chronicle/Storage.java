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

import static open.vincentf13.service.spot.infra.Constants.Store;

/**
  系統存儲中心 (Storage) - 系統的「記憶」與「血管」
  
  架構設計：
  1. 記憶 (Memory): 使用 Chronicle Map 存儲即時狀態。這是一種基於內存映射文件 (MMF) 的 Key-Value 存儲，
     支援跨進程共享、Zero-GC 且具備持久化能力。
  2. 血管 (Vessels): 使用 Chronicle Queue 傳遞指令與回報。這是一種順序寫入的日誌 (WAL)，
     作為系統的「事件流」，支援高效的重播與故障恢復。
 */
@Slf4j
@Component
public class Storage {
    private static Storage instance;
    /** 提供全系統靜態訪問點 */
    public static Storage self() { return instance; }

    @Value("${state.base-dir:" + Store.DEFAULT_BASE_DIR + "}")
    private String baseDir;

    @Value("${state.entries.orders:1000000}")
    private int orderEntries;

    @Value("${state.entries.balances:100000}")
    private int balanceEntries;

    @Value("${state.entries.index:1000000}")
    private int indexEntries;

    // --- 系統即時狀態 (Chronicle Maps) ---
    
    /** 用戶資產帳本 (userId + assetId -> Balance) */
    private ChronicleMap<BalanceKey, Balance> balances;
    /** 用戶擁有的資產索引。使用 64位 Long 的 Bitmask 記錄用戶有哪些幣種有餘額，用於 $O(1)$ 快速查詢 */
    private ChronicleMap<Long, Long> userAssets; 
    /** 全局訂單庫 (orderId -> Order) */
    private ChronicleMap<Long, Order> orders;
    /** 當前活躍掛單的 ID 索引。用於系統啟動時，快速找出哪些訂單需要載入內存訂單簿 */
    private ChronicleMap<Long, Boolean> activeOrders;
    /** 成交歷史紀錄 (tradeId -> Trade) */
    private ChronicleMap<Long, Trade> trades;
    /** 冪等性校驗表。記錄用戶 clientOrderId 與系統內部 orderId 的映射，防止重複下單 */
    private ChronicleMap<CidKey, Long> cids; 
    /** 系統進度元數據。存儲各組件已處理的 Sequence ID 與全局 ID 計數器，是恢復一致性的關鍵 */
    private ChronicleMap<Byte, Progress> metadata; 
    
    // --- 數據隊列 (Queues) ---
    /** 
      使用 RawWire [Fieldless Binary] 模式
      優點：極速效能，省去標籤解析與字串比對開銷。
      風險：格式脆弱，讀寫端的欄位順序必須完全一致且不可隨意變動。
      
      TODO: aeron raft 搭配 異步刷盤 (當前為了數據安全先使用同步刷盤)
     */
    private ChronicleQueue gatewayQueue;  
    private ChronicleQueue commandQueue;  
    private ChronicleQueue resultQueue;   

    /**
      系統啟動初始化
      建立或加載磁碟上的數據文件，並根據配置預分配內存空間
     */
    @PostConstruct
    public void init() throws IOException {
        if (!baseDir.endsWith("/")) baseDir += "/";
        new File(baseDir).mkdirs();

        balances = createMap(Store.BALANCES, BalanceKey.class, Balance.class, balanceEntries, new BalanceKey(), new Balance());
        userAssets = createMap(Store.USER_ASSETS, Long.class, Long.class, balanceEntries, 0L, 0L);
        orders = createMap(Store.ORDERS, Long.class, Order.class, orderEntries, 0L, new Order());
        activeOrders = createMap(Store.ACTIVE_ORDERS, Long.class, Boolean.class, orderEntries, 0L, true);
        trades = createMap(Store.TRADES, Long.class, Trade.class, indexEntries, 0L, new Trade());
        cids = createMap(Store.CIDS, CidKey.class, Long.class, indexEntries, new CidKey(), 0L);
        metadata = createMap(Store.METADATA, Byte.class, Progress.class, 100, (byte)0, new Progress());

        // --- 建立隊列，啟用同步刷盤以確保數據落地安全 ---
        gatewayQueue = SingleChronicleQueueBuilder.fieldlessBinary(new File(baseDir + Store.Q_GATEWAY)).sync(true).build();
        commandQueue = SingleChronicleQueueBuilder.fieldlessBinary(new File(baseDir + Store.Q_COMMAND)).sync(true).build();
        resultQueue = SingleChronicleQueueBuilder.fieldlessBinary(new File(baseDir + Store.Q_RESULT)).sync(true).build();
        
        instance = this;
        log.info("Chronicle Storage 核心組件初始化完成 (RawWire + Sync 模式)，存儲路徑: {}", baseDir);
    }

    /**
      建立 Chronicle Map 的通用輔助方法
      @param name Map 的名稱，用於日誌識別與檔案命名
      @param k Key 的類別
      @param v Value 的類別
      @param entries 預分配的總條目數。Chronicle Map 是靜態空間分配，此值決定了檔案的物理大小。
      @param avgKey 平均 Key 的範例。用於讓 Chronicle 精確計算序列化長度，減少內存碎片並優化尋址。
      @param avgValue 平均 Value 的範例。
     */
    private <K, V> ChronicleMap<K, V> createMap(String name, Class<K> k, Class<V> v, int entries, K avgKey, V avgValue) throws IOException {
        return ChronicleMap.of(k, v)
                .name(name) // 設定 Map 邏輯名稱，用於日誌識別與排查
                .entries(entries) // 核心參數：預計存儲的資料筆數，會影響雜湊桶 (Hash Buckets) 的分配
                .averageKey(avgKey) // 效能關鍵：提供平均 Key 樣本，避免為變長欄位分配過大空間
                .averageValue(avgValue) // 效能關鍵：提供平均 Value 樣本
                .createPersistedTo(new File(baseDir + name + ".dat")); // 物理持久化：將內存數據映射至磁碟檔案
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

    /**
      系統關閉時，安全釋放內存映射檔案
     */
    @PreDestroy
    public void close() {
        closeQuietly(balances); closeQuietly(userAssets); closeQuietly(orders);
        closeQuietly(activeOrders); closeQuietly(trades); closeQuietly(cids); closeQuietly(metadata);
        closeQuietly(gatewayQueue); closeQuietly(commandQueue); closeQuietly(resultQueue);
    }

    private void closeQuietly(AutoCloseable c) { try { if (c != null) c.close(); } catch (Exception ignored) {} }
}
