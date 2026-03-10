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
 1. 記憶 (Memory): 使用 Chronicle Map 存儲即時狀態。
 2. 血管 (Vessels): 使用 Chronicle Queue 傳遞指令與回報。
 */
@Slf4j
@Component
public class Storage {
    private static Storage instance;
    
    /**
     提供全系統靜態訪問點
     */
    public static Storage self() {
        return instance;
    }
    
    @Value("${state.base-dir:" + Store.DEFAULT_BASE_DIR + "}")
    private String baseDir;
    
    @Value("${state.entries.orders:1000000}")
    private int orderEntries;
    
    @Value("${state.entries.balances:100000}")
    private int balanceEntries;
    
    @Value("${state.entries.index:1000000}")
    private int indexEntries;
    
    // --- 系統即時狀態 (Chronicle Maps) ---
    private ChronicleMap<BalanceKey, Balance> balances;
    private ChronicleMap<Long, Long> userAssets;
    private ChronicleMap<Long, Order> orders;
    private ChronicleMap<Long, Boolean> activeOrders;
    private ChronicleMap<Long, Trade> trades;
    private ChronicleMap<CidKey, Long> cids;
    private ChronicleMap<Byte, Progress> metadata;
    
    // --- 數據隊列 (Queues) ---
    /**
     使用 RawWire [Fieldless Binary] 模式
     優點：極速效能，省去標籤解析與字串比對開銷。
     風險：格式脆弱，讀寫端的欄位順序必須完全一致且不可隨意變動。
     */
    private ChronicleQueue gatewayQueue;
    private ChronicleQueue commandQueue;
    private ChronicleQueue resultQueue;
    
    @PostConstruct
    public void init() throws IOException {
        if (!baseDir.endsWith("/"))
            baseDir += "/";
        new File(baseDir).mkdirs();
        
        balances = ChronicleMap.of(BalanceKey.class, Balance.class)
                               .name(Store.BALANCES)
                               .entries(balanceEntries)
                               .averageKey(new BalanceKey())
                               .averageValue(new Balance())
                               .createPersistedTo(new File(baseDir + Store.BALANCES + ".dat"));
        userAssets = ChronicleMap.of(Long.class, Long.class)
                                 .name(Store.USER_ASSETS)
                                 .entries(balanceEntries)
                                 .averageKey(0L)
                                 .averageValue(0L)
                                 .createPersistedTo(new File(baseDir + Store.USER_ASSETS + ".dat"));
        orders = ChronicleMap.of(Long.class, Order.class)
                             .name(Store.ORDERS)
                             .entries(orderEntries)
                             .averageKey(0L)
                             .averageValue(new Order())
                             .createPersistedTo(new File(baseDir + Store.ORDERS + ".dat"));
        activeOrders = ChronicleMap.of(Long.class, Boolean.class)
                                   .name(Store.ACTIVE_ORDERS)
                                   .entries(orderEntries)
                                   .averageKey(0L)
                                   .averageValue(true)
                                   .createPersistedTo(new File(baseDir + Store.ACTIVE_ORDERS + ".dat"));
        trades = ChronicleMap.of(Long.class, Trade.class)
                             .name(Store.TRADES)
                             .entries(indexEntries)
                             .averageKey(0L)
                             .averageValue(new Trade())
                             .createPersistedTo(new File(baseDir + Store.TRADES + ".dat"));
        cids = ChronicleMap.of(CidKey.class, Long.class)
                           .name(Store.CIDS)
                           .entries(indexEntries)
                           .averageKey(new CidKey())
                           .averageValue(0L)
                           .createPersistedTo(new File(baseDir + Store.CIDS + ".dat"));
        metadata = ChronicleMap.of(Byte.class, Progress.class)
                               .name(Store.METADATA)
                               .entries(100)
                               .averageKey((byte) 0)
                               .averageValue(new Progress())
                               .createPersistedTo(new File(baseDir + Store.METADATA + ".dat"));
        
        // --- 升級為 Fieldless Binary 模式 ---
        gatewayQueue = SingleChronicleQueueBuilder.fieldlessBinary(new File(baseDir + Store.Q_GATEWAY)).build();
        commandQueue = SingleChronicleQueueBuilder.fieldlessBinary(new File(baseDir + Store.Q_COMMAND)).build();
        resultQueue = SingleChronicleQueueBuilder.fieldlessBinary(new File(baseDir + Store.Q_RESULT)).build();
        
        instance = this;
        log.info("Chronicle Storage 核心組件初始化完成 (RawWire 模式)，存儲路徑: {}", baseDir);
    }
    
    public ChronicleMap<BalanceKey, Balance> balances() {
        return balances;
    }
    
    public ChronicleMap<Long, Long> userAssets() {
        return userAssets;
    }
    
    public ChronicleMap<Long, Order> orders() {
        return orders;
    }
    
    public ChronicleMap<Long, Boolean> activeOrders() {
        return activeOrders;
    }
    
    public ChronicleMap<Long, Trade> trades() {
        return trades;
    }
    
    public ChronicleMap<CidKey, Long> cids() {
        return cids;
    }
    
    public ChronicleMap<Byte, Progress> metadata() {
        return metadata;
    }
    
    public ChronicleQueue gatewayQueue() {
        return gatewayQueue;
    }
    
    public ChronicleQueue commandQueue() {
        return commandQueue;
    }
    
    public ChronicleQueue resultQueue() {
        return resultQueue;
    }
    
    @PreDestroy
    public void close() {
        closeQuietly(balances);
        closeQuietly(userAssets);
        closeQuietly(orders);
        closeQuietly(activeOrders);
        closeQuietly(trades);
        closeQuietly(cids);
        closeQuietly(metadata);
        closeQuietly(gatewayQueue);
        closeQuietly(commandQueue);
        closeQuietly(resultQueue);
    }
    
    private void closeQuietly(AutoCloseable c) {
        try {
            if (c != null)
                c.close();
        } catch (Exception ignored) {
        }
    }
}
