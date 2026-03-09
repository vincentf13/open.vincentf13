package open.vincentf13.service.spot_exchange.core;

import open.vincentf13.service.spot_exchange.infra.StateStore;
import open.vincentf13.service.spot_exchange.model.Balance;
import open.vincentf13.service.spot_exchange.model.BalanceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 
  內存帳務處理器
  負責餘額的原子操作
 */
@Service
public class LedgerProcessor {
    private static final Logger log = LoggerFactory.getLogger(LedgerProcessor.class);
    private final StateStore stateStore;

    public LedgerProcessor(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    public Balance getOrCreateBalance(long userId, int assetId) {
        BalanceKey key = new BalanceKey(userId, assetId);
        Balance balance = stateStore.getBalanceMap().get(key);
        if (balance == null) {
            balance = new Balance();
            balance.setAvailable(0);
            balance.setFrozen(0);
            balance.setVersion(0);
            stateStore.getBalanceMap().put(key, balance);
        }
        return balance;
    }

    /** 
      增加可用餘額
     */
    public void addAvailable(long userId, int assetId, long amount) {
        if (amount <= 0) return;
        BalanceKey key = new BalanceKey(userId, assetId);
        Balance balance = getOrCreateBalance(userId, assetId);
        
        balance.setAvailable(balance.getAvailable() + amount);
        balance.setVersion(balance.getVersion() + 1); // 增加版本號，支援讀取端校驗
        
        stateStore.getBalanceMap().put(key, balance);
    }

    /** 
      凍結可用餘額 (下單)
     */
    public boolean tryFreeze(long userId, int assetId, long amount) {
        if (amount <= 0) return true;
        BalanceKey key = new BalanceKey(userId, assetId);
        Balance balance = getOrCreateBalance(userId, assetId);
        
        if (balance.getAvailable() < amount) {
            return false;
        }
        
        balance.setAvailable(balance.getAvailable() - amount);
        balance.setFrozen(balance.getFrozen() + amount);
        balance.setVersion(balance.getVersion() + 1);
        
        stateStore.getBalanceMap().put(key, balance);
        return true;
    }

    /** 
      扣除凍結金額 (成交)
     */
    public void deductFrozen(long userId, int assetId, long amount) {
        if (amount <= 0) return;
        BalanceKey key = new BalanceKey(userId, assetId);
        Balance balance = getOrCreateBalance(userId, assetId);
        
        balance.setFrozen(Math.max(0, balance.getFrozen() - amount));
        balance.setVersion(balance.getVersion() + 1);
        
        stateStore.getBalanceMap().put(key, balance);
    }
}
