package open.vincentf13.service.spot_exchange.core;

import net.openhft.chronicle.map.ChronicleMap;
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

    /** 
      初始化或獲取用戶資產
     */
    public Balance getOrCreateBalance(long userId, int assetId) {
        BalanceKey key = new BalanceKey(userId, assetId);
        ChronicleMap<BalanceKey, Balance> map = stateStore.getBalanceMap();
        
        Balance balance = map.get(key);
        if (balance == null) {
            balance = new Balance();
            balance.setAvailable(0);
            balance.setFrozen(0);
            balance.setVersion(0);
            map.put(key, balance);
            log.info("為用戶 {} 初始化資產 {}", userId, assetId);
        }
        return balance;
    }

    /** 
      增加可用餘額 (充值/成交獲得)
     */
    public void addAvailable(long userId, int assetId, long amount) {
        BalanceKey key = new BalanceKey(userId, assetId);
        Balance balance = getOrCreateBalance(userId, assetId);
        balance.setAvailable(balance.getAvailable() + amount);
        stateStore.getBalanceMap().put(key, balance);
    }

    /** 
      凍結可用餘額 (下單)
     */
    public boolean tryFreeze(long userId, int assetId, long amount) {
        BalanceKey key = new BalanceKey(userId, assetId);
        Balance balance = getOrCreateBalance(userId, assetId);
        
        if (balance.getAvailable() < amount) {
            return false;
        }
        
        balance.setAvailable(balance.getAvailable() - amount);
        balance.setFrozen(balance.getFrozen() + amount);
        stateStore.getBalanceMap().put(key, balance);
        return true;
    }
}
