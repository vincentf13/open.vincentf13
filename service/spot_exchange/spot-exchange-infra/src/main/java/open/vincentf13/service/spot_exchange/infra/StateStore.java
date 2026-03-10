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

/** 
  系統狀態中心 (簡化版：統一進度與狀態管理)
 */
@Component
public class StateStore {
    private ChronicleMap<BalanceKey, Balance> balanceMap;
    private ChronicleMap<Long, Long> userAssetIndexMap;
    private ChronicleMap<Long, ActiveOrder> orderMap;
    private ChronicleMap<Long, Boolean> activeOrderIdMap;
    private ChronicleMap<Long, TradeRecord> tradeHistoryMap;
    private ChronicleMap<CidKey, Long> cidMap;
    
    // 統一元數據 Map: (1=CoreProgress, 2=GatewaySeq, 3=OutboundSeq ...)
    private ChronicleMap<Byte, SystemProgress> metadataMap; 
    
    private ChronicleQueue gwQueue;
    private ChronicleQueue coreQueue;
    private ChronicleQueue outboundQueue;

    @PostConstruct
    public void init() throws IOException {
        String baseDir = "data/spot_exchange/";
        new File(baseDir).mkdirs();

        balanceMap = createMap(baseDir + "balances.dat", BalanceKey.class, Balance.class, "balances", 100_000);
        userAssetIndexMap = createMap(baseDir + "user_assets.dat", Long.class, Long.class, "user-assets", 100_000);
        orderMap = createMap(baseDir + "orders.dat", Long.class, ActiveOrder.class, "orders", 1_000_000);
        activeOrderIdMap = createMap(baseDir + "active_orders.dat", Long.class, Boolean.class, "active-idx", 100_000);
        tradeHistoryMap = createMap(baseDir + "trades.dat", Long.class, TradeRecord.class, "trades", 1_000_000);
        cidMap = createMap(baseDir + "cid_idx.dat", CidKey.class, Long.class, "cid-idx", 1_000_000);
        metadataMap = createMap(baseDir + "metadata.dat", Byte.class, SystemProgress.class, "metadata", 100);

        gwQueue = SingleChronicleQueueBuilder.binary(baseDir + "gw-queue").build();
        coreQueue = SingleChronicleQueueBuilder.binary(baseDir + "core-queue").build();
        outboundQueue = SingleChronicleQueueBuilder.binary(baseDir + "outbound-queue").build();
    }

    private <K, V> ChronicleMap<K, V> createMap(String path, Class<K> k, Class<V> v, String name, int entries) throws IOException {
        return ChronicleMap.of(k, v).name(name).entries(entries).createPersistedTo(new File(path));
    }

    public ChronicleMap<BalanceKey, Balance> getBalanceMap() { return balanceMap; }
    public ChronicleMap<Long, Long> getUserAssetIndexMap() { return userAssetIndexMap; }
    public ChronicleMap<Long, ActiveOrder> getOrderMap() { return orderMap; }
    public ChronicleMap<Long, Boolean> getActiveOrderIdMap() { return activeOrderIdMap; }
    public ChronicleMap<Long, TradeRecord> getTradeHistoryMap() { return tradeHistoryMap; }
    public ChronicleMap<CidKey, Long> getCidMap() { return cidMap; }
    public ChronicleMap<Byte, SystemProgress> getMetadataMap() { return metadataMap; }
    
    public ChronicleQueue getGwQueue() { return gwQueue; }
    public ChronicleQueue getCoreQueue() { return coreQueue; }
    public ChronicleQueue getOutboundQueue() { return outboundQueue; }

    @PreDestroy
    public void close() {
        balanceMap.close(); userAssetIndexMap.close(); orderMap.close(); 
        activeOrderIdMap.close(); tradeHistoryMap.close(); cidMap.close(); metadataMap.close();
        gwQueue.close(); coreQueue.close(); outboundQueue.close();
    }
}
