package open.vincentf13.service.spot_exchange.core;

import open.vincentf13.service.spot_exchange.infra.StateStore;
import open.vincentf13.service.spot_exchange.model.Balance;
import open.vincentf13.service.spot_exchange.model.BalanceKey;
import org.springframework.stereotype.Service;
import net.openhft.chronicle.map.ExternalMapQueryContext;

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
                updateUserAssetIndex(userId, assetId);
            } else {
                entry.replaceValue(context.wrapValueAsData(balance));
            }
        }
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
