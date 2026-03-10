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
    private ChronicleMap<BalanceKey, Balance> balanceMap;
    private ChronicleMap<Long, Long> userAssetIndexMap; // userId -> AssetId Bitmask ($O(1)$ discovery)
    private ChronicleMap<Long, ActiveOrder> orderMap;
    private ChronicleMap<Long, Boolean> activeOrderIdMap;
    private ChronicleMap<Long, TradeRecord> tradeHistoryMap;
    private ChronicleMap<CidKey, Long> cidMap;
    private ChronicleMap<String, Long> systemStateMap;
    
    private ChronicleQueue gwQueue;
    private ChronicleQueue coreQueue;
    private ChronicleQueue outboundQueue;

    @PostConstruct
    public void init() throws IOException {
        String baseDir = "data/spot_exchange/";
        new File(baseDir).mkdirs();

        balanceMap = ChronicleMap.of(BalanceKey.class, Balance.class)
            .name("balance-map").entries(100_000)
            .averageKey(new BalanceKey(0, 0)).averageValue(new Balance())
            .createPersistedTo(new File(baseDir + "balances.dat"));

        userAssetIndexMap = ChronicleMap.of(Long.class, Long.class)
            .name("user-asset-index").entries(100_000)
            .averageKey(0L).averageValue(0L)
            .createPersistedTo(new File(baseDir + "user_assets.dat"));

        orderMap = ChronicleMap.of(Long.class, ActiveOrder.class)
            .name("order-map").entries(1_000_000)
            .averageKey(0L).averageValue(new ActiveOrder())
            .createPersistedTo(new File(baseDir + "orders.dat"));

        activeOrderIdMap = ChronicleMap.of(Long.class, Boolean.class)
            .name("active-orders-map").entries(100_000)
            .averageKey(0L).averageValue(true)
            .createPersistedTo(new File(baseDir + "active_orders.dat"));

        tradeHistoryMap = ChronicleMap.of(Long.class, TradeRecord.class)
            .name("trade-history-map").entries(1_000_000)
            .averageKey(0L).averageValue(new TradeRecord())
            .createPersistedTo(new File(baseDir + "trades.dat"));

        cidMap = ChronicleMap.of(CidKey.class, Long.class)
            .name("cid-map").entries(1_000_000)
            .averageKey(new CidKey(0, "client_order_id_placeholder_001"))
            .averageValue(0L)
            .createPersistedTo(new File(baseDir + "cid_index.dat"));

        systemStateMap = ChronicleMap.of(String.class, Long.class)
            .name("system-state-map").entries(20)
            .averageKey("lastProcessedSeq")
            .averageValue(0L)
            .createPersistedTo(new File(baseDir + "system_state.dat"));

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
    public ChronicleMap<String, Long> getSystemStateMap() { return systemStateMap; }
    public ChronicleQueue getGwQueue() { return gwQueue; }
    public ChronicleQueue getCoreQueue() { return coreQueue; }
    public ChronicleQueue getOutboundQueue() { return outboundQueue; }

    @PreDestroy
    public void close() {
        if (balanceMap != null) balanceMap.close();
        if (userAssetIndexMap != null) userAssetIndexMap.close();
        if (orderMap != null) orderMap.close();
        if (activeOrderIdMap != null) activeOrderIdMap.close();
        if (tradeHistoryMap != null) tradeHistoryMap.close();
        if (cidMap != null) cidMap.close();
        if (systemStateMap != null) systemStateMap.close();
        if (gwQueue != null) gwQueue.close();
        if (coreQueue != null) coreQueue.close();
        if (outboundQueue != null) outboundQueue.close();
    }
}
