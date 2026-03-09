package open.vincentf13.service.spot_exchange.query;

import net.openhft.chronicle.map.ExternalMapQueryContext;
import open.vincentf13.service.spot_exchange.infra.StateStore;
import open.vincentf13.service.spot_exchange.model.ActiveOrder;
import open.vincentf13.service.spot_exchange.model.Balance;
import open.vincentf13.service.spot_exchange.model.BalanceKey;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 
  高性能查詢服務 (金融級一致性讀取版)
 */
@Service
public class QueryService {
    private final StateStore stateStore;

    public QueryService(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    public List<Balance> getUserBalances(long userId) {
        List<Balance> results = new ArrayList<>();
        // 遍歷 KeySet 以定位屬於該用戶的資產
        stateStore.getBalanceMap().keySet().forEach(key -> {
            if (key.getUserId() == userId) {
                // --- 深度優化：使用 Map Context 鎖定 Entry 讀取，確保跨進程原子性 ---
                try (ExternalMapQueryContext<BalanceKey, Balance, ?> context = 
                         stateStore.getBalanceMap().queryContext(key)) {
                    context.readLock().lock();
                    if (context.entry() != null) {
                        results.add(context.entry().value().get());
                    }
                }
            }
        });
        return results;
    }

    public List<ActiveOrder> getActiveOrders(long userId) {
        List<ActiveOrder> results = new ArrayList<>();
        stateStore.getActiveOrderIdMap().keySet().forEach(orderId -> {
            // --- 深度優化：確保讀取的訂單狀態不是 MatchingEngine 寫入到一半的數據 ---
            try (ExternalMapQueryContext<Long, ActiveOrder, ?> context = 
                     stateStore.getOrderMap().queryContext(orderId)) {
                context.readLock().lock();
                if (context.entry() != null) {
                    ActiveOrder order = context.entry().value().get();
                    if (order.getUserId() == userId) {
                        results.add(order);
                    }
                }
            }
        });
        return results;
    }
}
