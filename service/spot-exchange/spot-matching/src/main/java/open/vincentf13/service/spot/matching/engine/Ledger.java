package open.vincentf13.service.spot.matching.engine;

import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import org.springframework.stereotype.Service;

/**
  內存帳務處理器 (Ledger)
  負責所有資產的凍結、扣款、結算與退款
  實施「序列標籤 (lastSeq)」防禦，確保狀態更新的冪等性與強一致性
 */
@Service
public class Ledger {
    /**
      標準成交結算
     */
    public void tradeSettle(long userId, int assetOut, long amountOut, int assetIn, long amountIn, long currentSeq) {
        updateBalance(userId, assetOut, currentSeq, b -> b.setFrozen(Math.max(0, b.getFrozen() - amountOut)));
        updateBalance(userId, assetIn, currentSeq, b -> b.setAvailable(b.getAvailable() + amountIn));
    }

    /**
      成交結算並處理退款 (用於處理 Taker 價格改進)
     */
    public void tradeSettleWithRefund(long userId, int assetOut, long amountOut, long totalFrozen, int assetIn, long amountIn, long currentSeq) {
        updateBalance(userId, assetOut, currentSeq, b -> {
            b.setFrozen(Math.max(0, b.getFrozen() - totalFrozen));
            long refund = totalFrozen - amountOut; // 計算價格改進導致的溢價退款
            if (refund > 0) b.setAvailable(b.getAvailable() + refund);
        });
        updateBalance(userId, assetIn, currentSeq, b -> b.setAvailable(b.getAvailable() + amountIn));
    }

    /**
      原子更新資產餘額
     */
    public void updateBalance(long userId, int assetId, long currentSeq, java.util.function.Consumer<Balance> action) {
        BalanceKey key = new BalanceKey(userId, assetId);
        Balance balance = Storage.self().balances().get(key);
        if (balance == null) {
            balance = new Balance();
            balance.setAvailable(0); balance.setFrozen(0); balance.setVersion(0); balance.setLastSeq(-1);
        }
        
        // 核心防禦：如果當前指令序號已處理過，則跳過更新，確保重播冪等
        if (balance.getLastSeq() >= currentSeq) return;
        
        action.accept(balance);
        balance.setVersion(balance.getVersion() + 1);
        balance.setLastSeq(currentSeq);
        
        Storage.self().balances().put(key, balance);
        updateUserAssetIndex(userId, assetId);
    }

    /**
      維護用戶資產 Bitmask 索引
     */
    private void updateUserAssetIndex(long userId, int assetId) {
        if (assetId < 0 || assetId >= 64) return;
        long currentMask = Storage.self().userAssets().getOrDefault(userId, 0L);
        long newMask = currentMask | (1L << assetId);
        if (currentMask != newMask) Storage.self().userAssets().put(userId, newMask);
    }

    public void initBalance(long userId, int assetId, long currentSeq) {
        updateBalance(userId, assetId, currentSeq, b -> {});
    }

    public void addAvailable(long userId, int assetId, long currentSeq, long amount) {
        updateBalance(userId, assetId, currentSeq, b -> b.setAvailable(b.getAvailable() + amount));
    }

    /**
      嘗試凍結資產 (風控前置)
     */
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
