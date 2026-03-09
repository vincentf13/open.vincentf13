package open.vincentf13.service.spot_exchange.core;

import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot_exchange.model.ActiveOrder;
import open.vincentf13.service.spot_exchange.model.Balance;
import open.vincentf13.service.spot_exchange.model.BalanceKey;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;

/** 
  系統狀態存儲管理類
  負責管理 Chronicle Map 的生命週期與映射文件
 */
@Component
public class StateStore {
    private ChronicleMap<BalanceKey, Balance> balanceMap;
    private ChronicleMap<Long, ActiveOrder> orderMap;

    @PostConstruct
    public void init() throws IOException {
        String baseDir = "data/spot_exchange/";
        new File(baseDir).mkdirs();

        balanceMap = ChronicleMap
            .of(BalanceKey.class, Balance.class)
            .name("balance-map")
            .entries(100_000)
            .averageKey(new BalanceKey(0, 0))
            .averageValue(new Balance())
            .createPersistedTo(new File(baseDir + "balances.dat"));

        orderMap = ChronicleMap
            .of(Long.class, ActiveOrder.class)
            .name("order-map")
            .entries(1_000_000)
            .averageKey(0L)
            .averageValue(new ActiveOrder())
            .createPersistedTo(new File(baseDir + "orders.dat"));
    }

    public ChronicleMap<BalanceKey, Balance> getBalanceMap() {
        return balanceMap;
    }

    public ChronicleMap<Long, ActiveOrder> getOrderMap() {
        return orderMap;
    }

    @PreDestroy
    public void close() {
        if (balanceMap != null) balanceMap.close();
        if (orderMap != null) orderMap.close();
    }
}
