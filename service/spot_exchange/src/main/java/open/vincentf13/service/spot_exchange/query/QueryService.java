package open.vincentf13.service.spot_exchange.query;

import open.vincentf13.service.spot_exchange.core.StateStore;
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
        // 遍歷 BalanceMap 獲取用戶資產 (MVP 可暫時遍歷，高性能建議維護 UserAssetIndex)
        for (Map.Entry<BalanceKey, Balance> entry : stateStore.getBalanceMap().entrySet()) {
            if (entry.getKey().getUserId() == userId) {
                results.add(entry.getValue());
            }
        }
        return results;
    }
}
