package open.vincentf13.service.spot.matching.engine;

import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/** 
 內存帳務處理器 (Ledger)
 職責：管理所有資產的凍結、扣款、結算與退款，是系統一致性的最後防線
 特性：
 1. 序列標籤防禦：利用 lastSeq 確保指令重播時的帳務冪等性。
 2. 確定性結算：所有計算均在內存中完成，不依賴外部時鐘。
 */
@Service
public class Ledger {

    /** 
      初始化用戶餘額記錄
      @param userId 用戶 ID
      @param assetId 資產 ID
      @param seq 當前指令序號
     */
    public void initBalance(long userId, int assetId, long seq) {
        updateBalance(userId, assetId, seq, b -> {});
    }

    /** 
      標準成交結算
      邏輯：Maker 扣除凍結，Taker 增加可用
     */
    public void tradeSettle(long userId, int assetOut, long amountOut, int assetIn, long amountIn, long seq) {
        updateBalance(userId, assetOut, seq, b -> b.setFrozen(Math.max(0, b.getFrozen() - amountOut)));
        updateBalance(userId, assetIn, seq, b -> b.setAvailable(b.getAvailable() + amountIn));
    }

    /** 
      成交結算並處理退款
      場景：當 Taker 成交價優於其限定價時，需退回多凍結的保證金
     */
    public void tradeSettleWithRefund(long userId, int assetOut, long amountOut, long totalFrozen, int assetIn, long amountIn, long seq) {
        updateBalance(userId, assetOut, seq, b -> {
            b.setFrozen(Math.max(0, b.getFrozen() - totalFrozen));
            long refund = totalFrozen - amountOut; 
            if (refund > 0) b.setAvailable(b.getAvailable() + refund);
        });
        updateBalance(userId, assetIn, seq, b -> b.setAvailable(b.getAvailable() + amountIn));
    }

    /** 
      增加用戶可用餘額 (充值或系統獎勵)
     */
    public void addAvailable(long userId, int assetId, long seq, long amount) {
        updateBalance(userId, assetId, seq, b -> b.setAvailable(b.getAvailable() + amount));
    }

    /** 
      嘗試凍結資產 (風控檢核)
      @return 是否凍結成功
     */
    public boolean tryFreeze(long userId, int assetId, long seq, long amount) {
        if (amount <= 0) return true;
        // 使用單元素陣列捕獲 Lambda 內部的狀態變更
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

    /** 
      解凍資產至可用餘額
     */
    public void unfreeze(long userId, int assetId, long seq, long amount) {
        updateBalance(userId, assetId, seq, b -> {
            long actual = Math.min(b.getFrozen(), amount);
            b.setFrozen(b.getFrozen() - actual);
            b.setAvailable(b.getAvailable() + actual);
        });
    }

    /** 
      核心餘額更新邏輯 (含冪等防禦)
     */
    private void updateBalance(long userId, int assetId, long seq, Consumer<Balance> action) {
        BalanceKey key = new BalanceKey(userId, assetId);
        Balance balance = Storage.self().balances().get(key);
        
        // 簡約原則：若餘額對象不存在，則進行初始化
        if (balance == null) {
            balance = new Balance();
            balance.setAvailable(0); balance.setFrozen(0); balance.setVersion(0); balance.setLastSeq(-1);
        }
        
        // 冪等性檢查：若此指令已處理，則直接跳過
        if (balance.getLastSeq() >= seq) return;
        
        action.accept(balance);
        balance.setVersion(balance.getVersion() + 1);
        balance.setLastSeq(seq);
        
        Storage.self().balances().put(key, balance);
        updateUserAssetIndex(userId, assetId);
    }

    /** 
      維護用戶持倉索引 (Bitmask)
     */
    private void updateUserAssetIndex(long userId, int assetId) {
        if (assetId < 0 || assetId >= 64) return;
        long currentMask = Storage.self().userAssets().getOrDefault(userId, 0L);
        long newMask = currentMask | (1L << assetId);
        if (currentMask != newMask) Storage.self().userAssets().put(userId, newMask);
    }
}
