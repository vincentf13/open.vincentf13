package open.vincentf13.service.spot_exchange.infra;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import open.vincentf13.service.spot_exchange.model.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class StateStore {
    @Value("${state.base-dir:data/spot-exchange/}")
    private String baseDir;

    @Value("${state.entries.orders:1000000}")
    private int orderEntries;

    @Value("${state.entries.balances:100000}")
    private int balanceEntries;

    private ChronicleMap<BalanceKey, Balance> balanceMap;
    private ChronicleMap<Long, Long> userAssetIndexMap;
    private ChronicleMap<Long, ActiveOrder> orderMap;
    private ChronicleMap<Long, Boolean> activeOrderIdMap;
    private ChronicleMap<Long, TradeRecord> tradeHistoryMap;
    private ChronicleMap<CidKey, Long> cidMap;
    
    // --- 簡化：統一使用 BytesMarshallable 儲存所有類型的元數據與位點 ---
    private ChronicleMap<Byte, SystemProgress> systemMetadataMap; 
    
    private ChronicleQueue gwQueue;
    private ChronicleQueue coreQueue;
    private ChronicleQueue outboundQueue;

    @PostConstruct
    public void init() throws IOException {
        if (!baseDir.endsWith("/")) baseDir += "/";
        new File(baseDir).mkdirs();

        balanceMap = ChronicleMap.of(BalanceKey.class, Balance.class)
                .name("balances")
                .entries(balanceEntries)
                .averageKey(new BalanceKey())
                .averageValue(new Balance())
                .createPersistedTo(new File(baseDir + "balances.dat"));

        userAssetIndexMap = ChronicleMap.of(Long.class, Long.class)
                .name("user-assets")
                .entries(balanceEntries)
                .createPersistedTo(new File(baseDir + "user_assets.dat"));

        orderMap = ChronicleMap.of(Long.class, ActiveOrder.class)
                .name("orders")
                .entries(orderEntries)
                .averageValue(new ActiveOrder()) // ActiveOrder 內含 String，建議給予樣本或平均大小
                .createPersistedTo(new File(baseDir + "orders.dat"));

        activeOrderIdMap = ChronicleMap.of(Long.class, Boolean.class)
                .name("active-idx")
                .entries(orderEntries)
                .createPersistedTo(new File(baseDir + "active_orders.dat"));

        tradeHistoryMap = ChronicleMap.of(Long.class, TradeRecord.class)
                .name("trades")
                .entries(orderEntries)
                .averageValue(new TradeRecord())
                .createPersistedTo(new File(baseDir + "trades.dat"));

        cidMap = ChronicleMap.of(CidKey.class, Long.class)
                .name("cid-idx")
                .entries(orderEntries)
                .averageKey(new CidKey())
                .createPersistedTo(new File(baseDir + "cid_idx.dat"));
        
        systemMetadataMap = ChronicleMap.of(Byte.class, SystemProgress.class)
                .name("metadata")
                .entries(100)
                .averageValue(new SystemProgress())
                .createPersistedTo(new File(baseDir + "system_metadata.dat"));

        gwQueue = SingleChronicleQueueBuilder.binary(baseDir + "gw-queue").build();
        coreQueue = SingleChronicleQueueBuilder.binary(baseDir + "core-queue").build();
        outboundQueue = SingleChronicleQueueBuilder.binary(baseDir + "outbound-queue").build();
    }

    public ChronicleMap<BalanceKey, Balance> getBalanceMap() { return balanceMap; }
    public ChronicleMap<Long, Long> getUserAssetIndexMap() { return userAssetIndexMap; }
    public ChronicleMap<Long, ActiveOrder> getOrderMap() { return orderMap; }
    public ChronicleMap<Long, Boolean> getActiveOrderIdMap() { return activeOrderIdMap; }
    public ChronicleMap<Long, TradeRecord> getTradeHistoryMap() { return tradeHistoryMap; }
    public ChronicleMap<CidKey, Long> getCidMap() { return cidMap; }
    public ChronicleMap<Byte, SystemProgress> getSystemMetadataMap() { return systemMetadataMap; }
    
    public ChronicleQueue getGwQueue() { return gwQueue; }
    public ChronicleQueue getCoreQueue() { return coreQueue; }
    public ChronicleQueue getOutboundQueue() { return outboundQueue; }

    @PreDestroy
    public void close() {
        balanceMap.close(); userAssetIndexMap.close(); orderMap.close(); 
        activeOrderIdMap.close(); tradeHistoryMap.close(); cidMap.close(); systemMetadataMap.close();
        gwQueue.close(); coreQueue.close(); outboundQueue.close();
    }
}
