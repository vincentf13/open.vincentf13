package open.vincentf13.service.spot_exchange.core;

import net.openhft.chronicle.map.ExternalMapQueryContext;
import open.vincentf13.service.spot_exchange.infra.StateStore;
import open.vincentf13.service.spot_exchange.model.Balance;
import open.vincentf13.service.spot_exchange.model.BalanceKey;
import org.springframework.stereotype.Service;

/** 
  內存帳務處理器
  實作序列標籤 (Sequence Tagging) 以確保金融級一致性
 */
@Service
public class LedgerProcessor {
    private final StateStore stateStore;

    public LedgerProcessor(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    /** 
      更新資產餘額
      @param currentSeq 當前處理的 WAL 指令序號
     */
    public void updateBalance(long userId, int assetId, long currentSeq, java.util.function.Consumer<Balance> action) {
        BalanceKey key = new BalanceKey(userId, assetId);
        
        try (ExternalMapQueryContext<BalanceKey, Balance, ?> context = stateStore.getBalanceMap().queryContext(key)) {
            context.writeLock().lock();
            net.openhft.chronicle.map.MapEntry<BalanceKey, Balance> entry = context.entry();
            
            Balance balance;
            if (entry == null) {
                balance = new Balance();
                balance.setAvailable(0);
                balance.setFrozen(0);
                balance.setVersion(0);
                balance.setLastSeq(-1);
            } else {
                balance = entry.value().get();
            }
            
            // --- 金融級一致性：序列檢查 ---
            // 如果此資產已由該序號（或更高序號）處理過，直接跳過物理寫入
            if (balance.getLastSeq() >= currentSeq) {
                return;
            }
            
            action.accept(balance);
            balance.setVersion(balance.getVersion() + 1);
            balance.setLastSeq(currentSeq); // 打上當前處理序號
            
            if (entry == null) {
                context.insert(context.absentEntry(), context.wrapValueAsData(balance));
            } else {
                entry.replaceValue(context.wrapValueAsData(balance));
            }
        }
    }

    public void initBalance(long userId, int assetId, long currentSeq) {
        updateBalance(userId, assetId, currentSeq, b -> {});
    }

    public void addAvailable(long userId, int assetId, long currentSeq, long amount) {
        if (amount <= 0) return;
        updateBalance(userId, assetId, currentSeq, b -> b.setAvailable(b.getAvailable() + amount));
    }

    public boolean tryFreeze(long userId, int assetId, long currentSeq, long amount) {
        if (amount <= 0) return true;
        
        boolean[] success = {false};
        updateBalance(userId, assetId, currentSeq, b -> {
            if (b.getAvailable() >= amount) {
                b.setAvailable(b.getAvailable() - amount);
                b.setFrozen(b.getFrozen() + amount);
                success[0] = true;
            }
        });
        return success[0];
    }

    public void deductFrozen(long userId, int assetId, long currentSeq, long amount) {
        if (amount <= 0) return;
        updateBalance(userId, assetId, currentSeq, b -> b.setFrozen(Math.max(0, b.getFrozen() - amount)));
    }
}
