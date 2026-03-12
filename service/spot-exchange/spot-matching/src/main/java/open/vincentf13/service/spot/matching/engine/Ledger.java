package open.vincentf13.service.spot.matching.engine;

import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 內存帳務處理器 (Ledger)
 職責：管理全系統資產的一致性，處理凍結、結算與退款
 
 一致性保證：
 1. 原子性：帳務變更隨指令流順序執行，不設內部冪等攔截，以支援單筆指令內的多筆結算。
 2. 故障恢復：依賴 Engine 層級的指令重播，確保崩潰後的帳務狀態完全還原。
 */
@Service
public class Ledger {

    /** 
      標準成交結算
      邏輯：Maker 扣除凍結部分，Taker 增加可用資產
     */
    public void tradeSettle(long userId, int assetOut, long amountOut, int assetIn, long amountIn, long seq) {
        updateBalance(userId, assetOut, seq, b -> b.setFrozen(Math.max(0, b.getFrozen() - amountOut)));
        updateBalance(userId, assetIn, seq, b -> b.setAvailable(b.getAvailable() + amountIn));
    }

    /** 
      成交結算並處理退款
      場景：當 Taker 的成交價優於其限定價時，需退回溢價部分的凍結金額
     */
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

    /** 
      核心餘額更新邏輯
      注意：此處移除 lastSeq 檢查以允許同一指令內的多筆資產變動
     */
    private void updateBalance(long userId, int assetId, long seq, Consumer<Balance> action) {
        BalanceKey key = new BalanceKey(userId, assetId);
        Balance balance = Storage.self().balances().get(key);
        
        if (balance == null) {
            balance = new Balance();
            balance.setAvailable(0); balance.setFrozen(0); balance.setVersion(0);
        }
        
        action.accept(balance);
        balance.setVersion(balance.getVersion() + 1);
        balance.setLastSeq(seq);
        
        Storage.self().balances().put(key, balance);
        updateUserAssetIndex(userId, assetId);
    }

    private void updateUserAssetIndex(long userId, int assetId) {
        if (assetId < 0 || assetId >= 64) return;
        long currentMask = Storage.self().userAssets().getOrDefault(userId, 0L);
        long newMask = currentMask | (1L << assetId);
        if (currentMask != newMask) Storage.self().userAssets().put(userId, newMask);
    }
}
