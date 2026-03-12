package open.vincentf13.service.spot.matching.engine;

import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import org.agrona.collections.Long2LongHashMap;
import org.springframework.stereotype.Service;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 內存帳務處理器 (Ledger) - 終極 Zero-Allocation 版
 職責：管理全系統資產的一致性與流轉，達成全路徑零對象分配
 */
@Service
public class Ledger {
    private final ChronicleMap<BalanceKey, Balance> balancesDiskMap = Storage.self().balances();
    private final ChronicleMap<Long, Long> userAssetBitmaskDiskMap = Storage.self().userAssets();
    
    /** 預分配位圖快取：避免 Rehashing 分配 */
    private final Long2LongHashMap bitmaskCache = new Long2LongHashMap(100_000, 0.5f, 0L);

    private final Balance reusableBalance = new Balance();
    private final BalanceKey reusableKey = new BalanceKey();
    
    /** 冷啟動專用物件：用於初始化磁碟中不存在的新帳戶，徹底消除 new Balance() */
    private final Balance coldStartReusable = new Balance();

    /** 執行成交結算 */
    public void settleTrade(long mUid, long tUid, long tradePrice, long tradeQty, byte takerSide, long takerPrice, long seq) {
        final long floor = DecimalUtil.mulFloor(tradePrice, tradeQty), ceil = DecimalUtil.mulCeil(tradePrice, tradeQty);

        if (takerSide == 0) { // Taker BUY
            final long takerFrozenTotal = DecimalUtil.mulCeil(takerPrice, tradeQty);
            access(tUid, Asset.USDT, takerFrozenTotal - ceil, -takerFrozenTotal, seq);
            access(tUid, Asset.BTC, tradeQty, 0, seq);
            access(mUid, Asset.BTC, 0, -tradeQty, seq);
            access(mUid, Asset.USDT, floor, 0, seq);
        } else { // Taker SELL
            access(tUid, Asset.BTC, 0, -tradeQty, seq);
            access(tUid, Asset.USDT, floor, 0, seq);
            access(mUid, Asset.USDT, 0, -ceil, seq);
            access(mUid, Asset.BTC, tradeQty, 0, seq);
        }
        if (ceil > floor) access(PLATFORM_USER_ID, Asset.USDT, ceil - floor, 0, seq);
    }

    public void increaseAvailable(long userId, int assetId, long amount, long seq) {
        access(userId, assetId, amount, 0, seq);
    }

    public boolean freezeBalance(long userId, int assetId, long amount, long seq) {
        reusableKey.set(userId, assetId);
        Balance b = balancesDiskMap.getUsing(reusableKey, reusableBalance);
        
        // 優化：使用 coldStartReusable 代替 new Balance()
        if (b == null) b = prepareNewAccount();
        
        if (b.getAvailable() >= amount) {
            b.setAvailable(b.getAvailable() - amount);
            b.setFrozen(b.getFrozen() + amount);
            b.setVersion(b.getVersion() + 1); b.setLastSeq(seq);
            balancesDiskMap.put(reusableKey, b);
            updateAssetIndex(userId, assetId);
            return true;
        }
        return false;
    }

    public void initAccount(long userId, int assetId, long seq) {
        access(userId, assetId, 0, 0, seq);
    }

    /** 核心變更入口：絕對零分配 */
    private void access(long userId, int assetId, long availDelta, long frozenDelta, long seq) {
        reusableKey.set(userId, assetId);
        Balance b = balancesDiskMap.getUsing(reusableKey, reusableBalance);
        
        // 優化：使用 coldStartReusable 代替 new Balance()
        if (b == null) b = prepareNewAccount();
        
        b.setAvailable(b.getAvailable() + availDelta);
        b.setFrozen(Math.max(0, b.getFrozen() + frozenDelta));
        b.setVersion(b.getVersion() + 1);
        b.setLastSeq(seq);
        
        balancesDiskMap.put(reusableKey, b);
        updateAssetIndex(userId, assetId);
    }

    /** 零分配初始化新帳戶數據 */
    private Balance prepareNewAccount() {
        coldStartReusable.setAvailable(0);
        coldStartReusable.setFrozen(0);
        coldStartReusable.setVersion(0);
        coldStartReusable.setLastSeq(-1);
        return coldStartReusable;
    }

    private void updateAssetIndex(long userId, int assetId) {
        if (assetId < 0 || assetId >= 64) return;
        long mask = bitmaskCache.get(userId);
        if (mask == 0) {
            Long diskMask = userAssetBitmaskDiskMap.get(userId);
            mask = (diskMask == null) ? 0L : diskMask;
        }
        long newMask = mask | (1L << assetId);
        if (mask != newMask) {
            bitmaskCache.put(userId, newMask);
            userAssetBitmaskDiskMap.put(userId, newMask);
        }
    }
}
