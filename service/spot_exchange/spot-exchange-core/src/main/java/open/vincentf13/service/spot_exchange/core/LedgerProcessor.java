package open.vincentf13.service.spot_exchange.core;

import net.openhft.chronicle.map.ExternalMapQueryContext;
import open.vincentf13.service.spot_exchange.infra.StateStore;
import open.vincentf13.service.spot_exchange.model.Balance;
import open.vincentf13.service.spot_exchange.model.BalanceKey;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class LedgerProcessor {
    private final StateStore stateStore;

    public LedgerProcessor(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    public void updateBalance(long userId, int assetId, long currentSeq, java.util.function.Consumer<Balance> action) {
        BalanceKey key = new BalanceKey(userId, assetId);
        try (ExternalMapQueryContext<BalanceKey, Balance, ?> context = stateStore.getBalanceMap().queryContext(key)) {
            context.writeLock().lock();
            net.openhft.chronicle.map.MapEntry<BalanceKey, Balance> entry = context.entry();
            Balance balance = (entry == null) ? new Balance() : entry.value().get();
            
            if (balance.getLastSeq() >= currentSeq) return;
            
            action.accept(balance);
            balance.setVersion(balance.getVersion() + 1);
            balance.setLastSeq(currentSeq);
            
            if (entry == null) {
                context.insert(context.absentEntry(), context.wrapValueAsData(balance));
                // --- 維護資產索引 ---
                updateUserAssetIndex(userId, assetId);
            } else {
                entry.replaceValue(context.wrapValueAsData(balance));
            }
        }
    }

    private void updateUserAssetIndex(long userId, int assetId) {
        // 在實際環境中，這應是一個 Chronicle Map<Long, int[]>
        // 目前 MVP 簡化邏輯或暫留，但設計上必須存在
    }

    public void initBalance(long userId, int assetId, long currentSeq) {
        updateBalance(userId, assetId, currentSeq, b -> {});
    }

    public void addAvailable(long userId, int assetId, long currentSeq, long amount) {
        updateBalance(userId, assetId, currentSeq, b -> b.setAvailable(b.getAvailable() + amount));
    }

    public boolean tryFreeze(long userId, int assetId, long currentSeq, long amount) {
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

    /** 
      扣除凍結資產並解凍剩餘部分 (金融級對沖邏輯)
      @param actualDeduct 實際成交應扣除金額
      @param totalFrozen 該次成交對應的原始凍結總額 (針對此 Qty 部分)
     */
    public void settlement(long userId, int assetId, long currentSeq, long actualDeduct, long totalFrozen) {
        updateBalance(userId, assetId, currentSeq, b -> {
            // 1. 扣除總凍結
            b.setFrozen(Math.max(0, b.getFrozen() - totalFrozen));
            // 2. 返還價格改進導致的差額 (totalFrozen - actualDeduct)
            long remainder = totalFrozen - actualDeduct;
            if (remainder > 0) {
                b.setAvailable(b.getAvailable() + remainder);
            }
        });
    }
}
