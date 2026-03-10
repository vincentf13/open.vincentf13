package open.vincentf13.service.spot.infra.store;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import open.vincentf13.service.spot.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/** 
  系統狀態中心 (簡化版：統一進度與狀態管理)
 */
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
    private ChronicleMap<Byte, SystemProgress> systemMetadataMap; 
    
    private ChronicleQueue gwQueue;
    private ChronicleQueue coreQueue;
    private ChronicleQueue outboundQueue;

    @PostConstruct
    public void init() throws IOException {
        if (!baseDir.endsWith("/")) baseDir += "/";
        new File(baseDir).mkdirs();

        balanceMap = createMap("balances", BalanceKey.class, Balance.class, balanceEntries, new BalanceKey(), new Balance());
        userAssetIndexMap = createMap("user-assets", Long.class, Long.class, balanceEntries, 0L, 0L);
        orderMap = createMap("orders", Long.class, ActiveOrder.class, orderEntries, 0L, new ActiveOrder());
        activeOrderIdMap = createMap("active-idx", Long.class, Boolean.class, orderEntries, 0L, true);
        tradeHistoryMap = createMap("trades", Long.class, TradeRecord.class, orderEntries, 0L, new TradeRecord());
        cidMap = createMap("cid-idx", CidKey.class, Long.class, orderEntries, new CidKey(), 0L);
        systemMetadataMap = createMap("metadata", Byte.class, SystemProgress.class, 100, (byte)0, new SystemProgress());

        gwQueue = SingleChronicleQueueBuilder.binary(baseDir + "gw-queue").build();
        coreQueue = SingleChronicleQueueBuilder.binary(baseDir + "core-queue").build();
        outboundQueue = SingleChronicleQueueBuilder.binary(baseDir + "outbound-queue").build();
    }

    private <K, V> ChronicleMap<K, V> createMap(String name, Class<K> k, Class<V> v, int entries, K avgKey, V avgValue) throws IOException {
        return ChronicleMap.of(k, v)
                .name(name)
                .entries(entries)
                .averageKey(avgKey)
                .averageValue(avgValue)
                .createPersistedTo(new File(baseDir + name + ".dat"));
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
        closeQuietly(balanceMap); closeQuietly(userAssetIndexMap); closeQuietly(orderMap);
        closeQuietly(activeOrderIdMap); closeQuietly(tradeHistoryMap); closeQuietly(cidMap);
        closeQuietly(systemMetadataMap);
        closeQuietly(gwQueue); closeQuietly(coreQueue); closeQuietly(outboundQueue);
    }

    private void closeQuietly(AutoCloseable c) {
        try { if (c != null) c.close(); } catch (Exception ignored) {}
    }
}
