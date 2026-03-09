package open.vincentf13.service.spot_exchange.query;

import open.vincentf13.service.spot_exchange.infra.StateStore;
import open.vincentf13.service.spot_exchange.model.ActiveOrder;
import open.vincentf13.service.spot_exchange.model.Balance;
import open.vincentf13.service.spot_exchange.model.BalanceKey;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        // 遍歷 BalanceMap (資產種類少，遍歷開銷可控)
        stateStore.getBalanceMap().forEach((key, balance) -> {
            if (key.getUserId() == userId) {
                results.add(balance);
            }
        });
        return results;
    }

    public List<ActiveOrder> getActiveOrders(long userId) {
        List<ActiveOrder> results = new ArrayList<>();
        // 遍歷 OrderMap (MVP 階段，生產環境建議使用 UserOrderIndex)
        stateStore.getOrderMap().forEach((orderId, order) -> {
            if (order.getUserId() == userId && order.getStatus() < 2) {
                results.add(order);
            }
        });
        return results;
    }
}
