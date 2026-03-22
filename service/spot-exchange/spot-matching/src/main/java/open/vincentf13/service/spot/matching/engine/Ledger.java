package open.vincentf13.service.spot.matching.engine;

import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import org.agrona.collections.Long2LongHashMap;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 內存帳務處理器 (Ledger)
 職責：管理資產流轉，具備精確的 Sequence 屏障以防重播導致的重複扣款
 */
@Slf4j
@Service
public class Ledger {
    private final ChronicleMap<BalanceKey, Balance> balancesDiskMap = Storage.self().balances();
    private final ChronicleMap<Long, Long> userAssetBitmaskDiskMap = Storage.self().userAssets();
    private final Long2LongHashMap bitmaskCache = new Long2LongHashMap(100_000, 0.5f, 0L);
    
    // 內存緩衝區：存儲本次循環中變動的餘額 (使用 Agrona 優化過的 Map)
    private final org.agrona.collections.Object2ObjectHashMap<BalanceKey, Balance> pendingBalances = new org.agrona.collections.Object2ObjectHashMap<>(10000, 0.5f);
    private final Long2LongHashMap pendingBitmasks = new Long2LongHashMap(1000, 0.5f, 0L);

    private final Balance reusableBalance = new Balance();
    private final BalanceKey reusableKey = new BalanceKey();

    @PostConstruct
    public void init() {
        log.info("Ledger 開始預加載用戶資產位元遮罩...");
        userAssetBitmaskDiskMap.forEach(bitmaskCache::put);
        log.info("預加載完成，共載入 {} 筆記錄", bitmaskCache.size());
    }

    /** 
      背景落地：由 Engine 執行緒在空閒時或定期調用
     */
    public void flush() {
        if (!pendingBalances.isEmpty()) {
            pendingBalances.forEach(balancesDiskMap::put);
            pendingBalances.clear();
        }
        if (!pendingBitmasks.isEmpty()) {
            pendingBitmasks.forEach(userAssetBitmaskDiskMap::put);
            pendingBitmasks.clear();
        }
    }

    public void settleTrade(long mUid, long tUid, long tradePrice, long tradeQty, byte takerSide, long takerPrice, long seq, int baseAssetId, int quoteAssetId, long tradeId) {
        final long floor = DecimalUtil.mulFloor(tradePrice, tradeQty), ceil = DecimalUtil.mulCeil(tradePrice, tradeQty);

        if (takerSide == OrderSide.BUY) {
            final long takerFrozenTotal = DecimalUtil.mulCeil(takerPrice, tradeQty);
            access(tUid, quoteAssetId, takerFrozenTotal - ceil, -takerFrozenTotal, seq, tradeId);
            access(tUid, baseAssetId, tradeQty, 0, seq, tradeId);
            access(mUid, baseAssetId, 0, -tradeQty, seq, tradeId);
            access(mUid, quoteAssetId, floor, 0, seq, tradeId);
        } else {
            access(tUid, baseAssetId, 0, -tradeQty, seq, tradeId);
            access(tUid, quoteAssetId, floor, 0, seq, tradeId);
            access(mUid, quoteAssetId, 0, -ceil, seq, tradeId);
            access(mUid, baseAssetId, tradeQty, 0, seq, tradeId);
        }
        if (ceil > floor) access(PLATFORM_USER_ID, quoteAssetId, ceil - floor, 0, seq, tradeId);
    }

    public void increaseAvailable(long userId, int assetId, long amount, long seq) {
        access(userId, assetId, amount, 0, seq, 0);
    }

    public boolean freezeBalance(long userId, int assetId, long amount, long seq) {
        Balance b = getBalance(userId, assetId);
        if (b == null) b = prepareNewAccount();
        
        if (b.getLastSeq() >= seq) return true;

        if (b.getAvailable() >= amount) {
            b.setAvailable(b.getAvailable() - amount);
            b.setFrozen(b.getFrozen() + amount);
            commit(userId, assetId, b, seq, 0);
            return true;
        }
        return false;
    }

    public void unfreezeBalance(long userId, int assetId, long amount, long seq) {
        Balance b = getBalance(userId, assetId);
        if (b == null) return;
        if (b.getLastSeq() >= seq) return;

        b.setAvailable(b.getAvailable() + amount);
        b.setFrozen(Math.max(0, b.getFrozen() - amount));
        commit(userId, assetId, b, seq, 0);
    }

    private Balance getBalance(long userId, int assetId) {
        reusableKey.set(userId, assetId);
        Balance b = pendingBalances.get(reusableKey);
        if (b == null) {
            b = balancesDiskMap.getUsing(reusableKey, new Balance());
        }
        return b;
    }

    private void access(long userId, int assetId, long availDelta, long frozenDelta, long seq, long tradeId) {
        Balance b = getBalance(userId, assetId);
        if (b == null) b = prepareNewAccount();

        if (tradeId > 0) {
            if (b.getLastTradeId() >= tradeId) return;
        } else {
            if (b.getLastSeq() >= seq && availDelta == 0 && frozenDelta == 0) return;
        }

        b.setAvailable(b.getAvailable() + availDelta);
        b.setFrozen(Math.max(0, b.getFrozen() + frozenDelta));
        commit(userId, assetId, b, seq, tradeId);
    }

    private Balance prepareNewAccount() {
        Balance b = new Balance();
        b.setAvailable(0); b.setFrozen(0);
        b.setVersion(0); b.setLastSeq(-1);
        b.setLastTradeId(0);
        return b;
    }

    private void commit(long userId, int assetId, Balance b, long seq, long tradeId) {
        b.setVersion(b.getVersion() + 1);
        b.setLastSeq(seq);
        if (tradeId > 0) b.setLastTradeId(tradeId);
        
        BalanceKey key = new BalanceKey(userId, assetId);
        pendingBalances.put(key, b);
        updateAssetIndex(userId, assetId);
    }

    private void updateAssetIndex(long userId, int assetId) {
        if (assetId < 0 || assetId >= 64) return;
        long mask = pendingBitmasks.get(userId);
        if (mask == 0) mask = bitmaskCache.get(userId);
        
        long newMask = mask | (1L << assetId);
        if (mask != newMask) {
            bitmaskCache.put(userId, newMask);
            pendingBitmasks.put(userId, newMask);
        }
    }

    public void rebuildAssetIndexes() {
        log.info("--- 開始重建帳本資產索引 (Bitmask) ---");
        bitmaskCache.clear();
        balancesDiskMap.forEach((key, balance) -> {
            if (balance.getAvailable() > 0 || balance.getFrozen() > 0) {
                long userId = key.getUserId();
                int assetId = key.getAssetId();
                if (assetId >= 0 && assetId < 64) {
                    long mask = bitmaskCache.get(userId);
                    bitmaskCache.put(userId, mask | (1L << assetId));
                }
            }
        });
        bitmaskCache.forEach(userAssetBitmaskDiskMap::put);
        log.info("✅ 帳本資產索引重建完成。");
    }

    public boolean hasAsset(long userId, int assetId) {
        if (assetId >= 0 && assetId < 64) {
            long mask = pendingBitmasks.get(userId);
            if (mask == 0) mask = bitmaskCache.get(userId);
            return (mask & (1L << assetId)) != 0;
        }
        reusableKey.set(userId, assetId);
        return pendingBalances.containsKey(reusableKey) || balancesDiskMap.containsKey(reusableKey);
    }

    public void initAccount(long userId, int assetId, long seq) {
        access(userId, assetId, 0, 0, seq, 0);
    }
}
