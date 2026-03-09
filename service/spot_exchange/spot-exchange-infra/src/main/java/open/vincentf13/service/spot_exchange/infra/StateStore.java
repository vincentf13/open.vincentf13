package open.vincentf13.service.spot_exchange.infra;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import open.vincentf13.service.spot_exchange.model.ActiveOrder;
import open.vincentf13.service.spot_exchange.model.Balance;
import open.vincentf13.service.spot_exchange.model.BalanceKey;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/** 
  系統狀態與隊列管理中心 (統一單例管理)
 */
@Component
public class StateStore {
    private ChronicleMap<BalanceKey, Balance> balanceMap;
    private ChronicleMap<Long, ActiveOrder> orderMap;
    
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

        orderMap = ChronicleMap.of(Long.class, ActiveOrder.class)
            .name("order-map").entries(1_000_000)
            .averageKey(0L).averageValue(new ActiveOrder())
            .createPersistedTo(new File(baseDir + "orders.dat"));

        gwQueue = SingleChronicleQueueBuilder.binary(baseDir + "gw-queue").build();
        coreQueue = SingleChronicleQueueBuilder.binary(baseDir + "core-queue").build();
        outboundQueue = SingleChronicleQueueBuilder.binary(baseDir + "outbound-queue").build();
    }

    public ChronicleMap<BalanceKey, Balance> getBalanceMap() { return balanceMap; }
    public ChronicleMap<Long, ActiveOrder> getOrderMap() { return orderMap; }
    public ChronicleQueue getGwQueue() { return gwQueue; }
    public ChronicleQueue getCoreQueue() { return coreQueue; }
    public ChronicleQueue getOutboundQueue() { return outboundQueue; }

    @PreDestroy
    public void close() {
        if (balanceMap != null) balanceMap.close();
        if (orderMap != null) orderMap.close();
        if (gwQueue != null) gwQueue.close();
        if (coreQueue != null) coreQueue.close();
        if (outboundQueue != null) outboundQueue.close();
    }
}
