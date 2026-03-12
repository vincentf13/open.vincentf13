package open.vincentf13.service.spot.matching.engine;

import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/** 
 內存帳務處理器 (Ledger)
 職責：管理全系統資產的一致性，處理凍結、結算與退款
 */
@Service
public class Ledger {
    /** 餘額主表：持久化存儲用戶各幣種的餘額狀態 (可用、凍結) */
    private final ChronicleMap<BalanceKey, Balance> balances = Storage.self().balances();
    
    /** 持倉索引：採用 Bitmask 記錄用戶擁有的資產類型，用於 Query 模組快速篩選非零帳戶 */
    private final ChronicleMap<Long, Long> userAssets = Storage.self().userAssets();

    public void tradeSettle(long userId, int assetOut, long amountOut, int assetIn, long amountIn, long seq) {
        updateBalance(userId, assetOut, seq, b -> b.setFrozen(Math.max(0, b.getFrozen() - amountOut)));
        updateBalance(userId, assetIn, seq, b -> b.setAvailable(b.getAvailable() + amountIn));
    }

    public void tradeSettleWithRefund(long userId, int assetOut, long amountOut, long totalFrozen, int assetIn, long amountIn, long seq) {
        updateBalance(userId, assetOut, seq, b -> {
            b.setFrozen(Math.max(0, b.getFrozen() - totalFrozen));
            long refund = totalFrozen - amountOut; 
            if (refund > 0) b.setAvailable(b.getAvailable() + refund);
        });
        updateBalance(userId, assetIn, seq, b -> b.setAvailable(b.getAvailable() + amountIn));
    }

    public void addAvailable(long userId, int assetId, long seq, long amount) {
        updateBalance(userId, assetId, seq, b -> b.setAvailable(b.getAvailable() + amount));
    }

    public boolean tryFreeze(long userId, int assetId, long seq, long amount) {
        if (amount <= 0) return true;
        boolean[] success = {false};
        updateBalance(userId, assetId, seq, b -> {
            if (b.getAvailable() >= amount) {
                b.setAvailable(b.getAvailable() - amount);
                b.setFrozen(b.getFrozen() + amount);
                success[0] = true;
            }
        });
        return success[0];
    }

    public void initBalance(long userId, int assetId, long seq) {
        updateBalance(userId, assetId, seq, b -> {});
    }

    private void updateBalance(long userId, int assetId, long seq, Consumer<Balance> action) {
        BalanceKey key = new BalanceKey(userId, assetId);
        Balance balance = balances.get(key);
        
        if (balance == null) {
            balance = new Balance();
            balance.setAvailable(0); balance.setFrozen(0); balance.setVersion(0);
        }
        
        action.accept(balance);
        balance.setVersion(balance.getVersion() + 1);
        balance.setLastSeq(seq);
        
        balances.put(key, balance);
        updateUserAssetIndex(userId, assetId);
    }

    private void updateUserAssetIndex(long userId, int assetId) {
        if (assetId < 0 || assetId >= 64) return;
        long currentMask = userAssets.getOrDefault(userId, 0L);
        long newMask = currentMask | (1L << assetId);
        if (currentMask != newMask) userAssets.put(userId, newMask);
    }
}
