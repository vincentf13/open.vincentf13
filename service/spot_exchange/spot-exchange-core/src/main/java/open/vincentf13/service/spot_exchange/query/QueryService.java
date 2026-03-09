package open.vincentf13.service.spot_exchange.query;

import open.vincentf13.service.spot_exchange.infra.StateStore;
import open.vincentf13.service.spot_exchange.model.ActiveOrder;
import open.vincentf13.service.spot_exchange.model.Balance;
import open.vincentf13.service.spot_exchange.model.BalanceKey;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 
  高性能查詢服務
  直接讀取堆外內存 (Shared Chronicle Map)
 */
@Service
public class QueryService {
    private final StateStore stateStore;

    public QueryService(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    public List<Balance> getUserBalances(long userId) {
        List<Balance> results = new ArrayList<>();
        stateStore.getBalanceMap().forEach((key, balance) -> {
            if (key.getUserId() == userId) {
                // --- 樂觀鎖讀取：確保數據在讀取期間未被 MatchingEngine 修改 ---
                Balance snapshot;
                long v1, v2;
                do {
                    v1 = balance.getVersion();
                    snapshot = new Balance();
                    snapshot.setAvailable(balance.getAvailable());
                    snapshot.setFrozen(balance.getFrozen());
                    snapshot.setVersion(v1);
                    v2 = balance.getVersion();
                } while (v1 != v2); // 如果版本號變更，說明讀取期間發生了寫入，重試
                
                results.add(snapshot);
            }
        });
        return results;
    }

    public List<ActiveOrder> getActiveOrders(long userId) {
        List<ActiveOrder> results = new ArrayList<>();
        stateStore.getOrderMap().forEach((orderId, order) -> {
            if (order.getUserId() == userId && order.getStatus() < 2) {
                results.add(order);
            }
        });
        return results;
    }
}
