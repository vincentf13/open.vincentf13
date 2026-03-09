package open.vincentf13.service.spot_exchange.core;

import net.openhft.chronicle.map.ExternalMapQueryContext;
import open.vincentf13.service.spot_exchange.infra.StateStore;
import open.vincentf13.service.spot_exchange.model.Balance;
import open.vincentf13.service.spot_exchange.model.BalanceKey;
import org.springframework.stereotype.Service;

/** 
  內存帳務處理器
  利用 Chronicle Map 的 Context 鎖機制保證原子性
 */
@Service
public class LedgerProcessor {
    private final StateStore stateStore;

    public LedgerProcessor(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    public void updateBalance(long userId, int assetId, java.util.function.Consumer<Balance> action) {
        BalanceKey key = new BalanceKey(userId, assetId);
        
        // --- 深度加固：使用寫鎖 (Map Context) 確保多欄位更新的原子性 ---
        try (ExternalMapQueryContext<BalanceKey, Balance, ?> context = stateStore.getBalanceMap().queryContext(key)) {
            context.writeLock().lock();
            net.openhft.chronicle.map.MapEntry<BalanceKey, Balance> entry = context.entry();
            
            Balance balance;
            if (entry == null) {
                balance = new Balance();
                balance.setAvailable(0);
                balance.setFrozen(0);
                balance.setVersion(0);
            } else {
                balance = entry.value().get();
            }
            
            action.accept(balance);
            balance.setVersion(balance.getVersion() + 1);
            
            if (entry == null) {
                context.insert(context.absentEntry(), context.wrapValueAsData(balance));
            } else {
                entry.replaceValue(context.wrapValueAsData(balance));
            }
        }
    }

    /** 
      初始化資產 (JIT)
     */
    public void initBalance(long userId, int assetId) {
        updateBalance(userId, assetId, b -> {});
    }

    /** 
      增加可用餘額
     */
    public void addAvailable(long userId, int assetId, long amount) {
        if (amount <= 0) return;
        updateBalance(userId, assetId, b -> b.setAvailable(b.getAvailable() + amount));
    }

    /** 
      嘗試凍結 (下單)
     */
    public boolean tryFreeze(long userId, int assetId, long amount) {
        if (amount <= 0) return true;
        
        // 這裡需要先讀取判斷，因此我們直接在 updateBalance 的邏輯中擴展
        boolean[] success = {false};
        updateBalance(userId, assetId, b -> {
            if (b.getAvailable() >= amount) {
                b.setAvailable(b.getAvailable() - amount);
                b.setFrozen(b.getFrozen() + amount);
                success[0] = true;
            }
        });
        return success[0];
    }

    /** 
      扣除凍結金額 (成交)
     */
    public void deductFrozen(long userId, int assetId, long amount) {
        if (amount <= 0) return;
        updateBalance(userId, assetId, b -> b.setFrozen(Math.max(0, b.getFrozen() - amount)));
    }
}
