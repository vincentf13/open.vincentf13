package open.vincentf13.service.spot_exchange.query;

import open.vincentf13.service.spot_exchange.infra.StateStore;
import open.vincentf13.service.spot_exchange.model.ActiveOrder;
import open.vincentf13.service.spot_exchange.model.Balance;
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
                Balance snapshot;
                long v1, v2;
                do {
                    v1 = balance.getVersion();
                    snapshot = new Balance();
                    snapshot.setAvailable(balance.getAvailable());
                    snapshot.setFrozen(balance.getFrozen());
                    snapshot.setVersion(v1);
                    snapshot.setLastSeq(balance.getLastSeq());
                    v2 = balance.getVersion();
                } while (v1 != v2);
                results.add(snapshot);
            }
        });
        return results;
    }

    public List<ActiveOrder> getActiveOrders(long userId) {
        List<ActiveOrder> results = new ArrayList<>();
        stateStore.getActiveOrderIdMap().keySet().forEach(orderId -> {
            ActiveOrder order = stateStore.getOrderMap().get(orderId);
            if (order != null && order.getUserId() == userId) {
                // --- 樂觀鎖讀取：確保訂單狀態一致性 ---
                ActiveOrder snapshot;
                long v1, v2;
                do {
                    v1 = order.getVersion();
                    snapshot = new ActiveOrder();
                    snapshot.setOrderId(order.getOrderId());
                    snapshot.setPrice(order.getPrice());
                    snapshot.setQty(order.getQty());
                    snapshot.setFilled(order.getFilled());
                    snapshot.setStatus(order.getStatus());
                    snapshot.setVersion(v1);
                    v2 = order.getVersion();
                } while (v1 != v2);
                results.add(snapshot);
            }
        });
        return results;
    }
}
