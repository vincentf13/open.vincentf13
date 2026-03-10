package open.vincentf13.service.spot.matching.processor;

import open.vincentf13.service.spot.infra.store.StateStore;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import org.springframework.stereotype.Service;

/** 
  內存帳務處理器 (金融級原子更新與資產索引版)
 */
@Service
public class LedgerProcessor {
    private final StateStore stateStore;

    public LedgerProcessor(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    public void tradeSettle(long userId, int assetOut, long amountOut, int assetIn, long amountIn, long currentSeq) {
        updateBalance(userId, assetOut, currentSeq, b -> b.setFrozen(Math.max(0, b.getFrozen() - amountOut)));
        updateBalance(userId, assetIn, currentSeq, b -> b.setAvailable(b.getAvailable() + amountIn));
    }

    public void tradeSettleWithRefund(long userId, int assetOut, long amountOut, long totalFrozen, int assetIn, long amountIn, long currentSeq) {
        updateBalance(userId, assetOut, currentSeq, b -> {
            b.setFrozen(Math.max(0, b.getFrozen() - totalFrozen));
            long refund = totalFrozen - amountOut;
            if (refund > 0) b.setAvailable(b.getAvailable() + refund);
        });
        updateBalance(userId, assetIn, currentSeq, b -> b.setAvailable(b.getAvailable() + amountIn));
    }

    public void updateBalance(long userId, int assetId, long currentSeq, java.util.function.Consumer<Balance> action) {
        BalanceKey key = new BalanceKey(userId, assetId);
        
        Balance balance = stateStore.getBalanceMap().get(key);
        if (balance == null) {
            balance = new Balance();
            balance.setAvailable(0);
            balance.setFrozen(0);
            balance.setVersion(0);
            balance.setLastSeq(-1);
        }
        
        if (balance.getLastSeq() >= currentSeq) return;
        
        action.accept(balance);
        balance.setVersion(balance.getVersion() + 1);
        balance.setLastSeq(currentSeq);
        
        stateStore.getBalanceMap().put(key, balance);
        updateUserAssetIndex(userId, assetId);
    }

    private void updateUserAssetIndex(long userId, int assetId) {
        if (assetId < 0 || assetId >= 64) return;
        long currentMask = stateStore.getUserAssetIndexMap().getOrDefault(userId, 0L);
        long newMask = currentMask | (1L << assetId);
        if (currentMask != newMask) {
            stateStore.getUserAssetIndexMap().put(userId, newMask);
        }
    }

    public void initBalance(long userId, int assetId, long currentSeq) {
        updateBalance(userId, assetId, currentSeq, b -> {});
    }

    public void addAvailable(long userId, int assetId, long currentSeq, long amount) {
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

    public void unfreeze(long userId, int assetId, long currentSeq, long amount) {
        updateBalance(userId, assetId, currentSeq, b -> {
            long actual = Math.min(b.getFrozen(), amount);
            b.setFrozen(b.getFrozen() - actual);
            b.setAvailable(b.getAvailable() + actual);
        });
    }
}
