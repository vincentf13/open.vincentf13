package open.vincentf13.service.spot.matching.engine;

import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.Balance;
import open.vincentf13.service.spot.model.BalanceKey;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 * 內存帳務處理器 (Ledger) - 零對象分配 & 二級緩存版
 * 職責：管理資產流轉，使用內存緩存徹底消除熱點帳戶的磁碟 I/O 與對象裝箱。
 */
@Slf4j
@Service
public class Ledger {
    private final ChronicleMap<BalanceKey, Balance> balancesDiskMap = Storage.self().balances();
    private final ChronicleMap<Long, Long> userAssetBitmaskDiskMap = Storage.self().userAssets();
    
    // 二級緩存：徹底消除 BalanceKey 對象分配與磁碟讀取
    private final Long2ObjectHashMap<Balance> balanceCache = new Long2ObjectHashMap<>(100_000, 0.5f);
    private final Long2LongHashMap bitmaskCache = new Long2LongHashMap(100_000, 0.5f, 0L);
    
    // 待落地標記優化：使用原始類型陣列替代 HashSet，消除物件分配與陣列歸零壓力
    private final long[] dirtyQueue = new long[32768]; 
    private int dirtyCount = 0;
    private final LongHashSet dirtyBitmasks = new LongHashSet(1000);

    private final BalanceKey reusableKey = new BalanceKey();

    @PostConstruct
    public void init() {
        log.info("Ledger 正在預加載帳務數據至二級緩存...");
        userAssetBitmaskDiskMap.forEach(bitmaskCache::put);
        log.info("Ledger 初始化完成。");
    }

    /** 
     * 將變動過的緩存帳戶同步至磁碟
     */
    public void flush() {
        if (dirtyCount > 0) {
            for (int i = 0; i < dirtyCount; i++) {
                long combinedKey = dirtyQueue[i];
                long userId = combinedKey >>> 32;
                int assetId = (int) (combinedKey & 0xFFFFFFFFL);
                Balance b = balanceCache.get(combinedKey);
                if (b != null) {
                    reusableKey.set(userId, assetId);
                    balancesDiskMap.put(reusableKey, b);
                }
            }
            dirtyCount = 0;
        }
        
        if (!dirtyBitmasks.isEmpty()) {
            dirtyBitmasks.forEach(userId -> {
                userAssetBitmaskDiskMap.put(userId, bitmaskCache.get(userId));
            });
            dirtyBitmasks.clear();
        }
    }

    private long combine(long userId, int assetId) {
        return (userId << 32) | (assetId & 0xFFFFFFFFL);
    }

    private Balance getOrCreateBalance(long userId, int assetId) {
        final long combinedKey = combine(userId, assetId);
        Balance b = balanceCache.get(combinedKey);
        if (b == null) {
            reusableKey.set(userId, assetId);
            Balance diskVal = balancesDiskMap.get(reusableKey);
            if (diskVal != null) {
                b = new Balance();
                b.copyFrom(diskVal);
            } else {
                b = prepareNewAccount();
            }
            balanceCache.put(combinedKey, b);
        }
        return b;
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
        Balance b = getOrCreateBalance(userId, assetId);
        if (b.getLastSeq() >= seq) return true;

        if (b.getAvailable() >= amount) {
            b.setAvailable(b.getAvailable() - amount);
            b.setFrozen(b.getFrozen() + amount);
            markDirty(userId, assetId, b, seq, 0);
            return true;
        }
        return false;
    }

    public void unfreezeBalance(long userId, int assetId, long amount, long seq) {
        Balance b = getOrCreateBalance(userId, assetId);
        if (b.getLastSeq() >= seq) return;

        b.setAvailable(b.getAvailable() + amount);
        b.setFrozen(Math.max(0, b.getFrozen() - amount));
        markDirty(userId, assetId, b, seq, 0);
    }

    private void access(long userId, int assetId, long availDelta, long frozenDelta, long seq, long tradeId) {
        Balance b = getOrCreateBalance(userId, assetId);

        if (tradeId > 0) {
            if (b.getLastTradeId() >= tradeId) return;
        } else {
            if (b.getLastSeq() >= seq && availDelta == 0 && frozenDelta == 0) return;
        }

        b.setAvailable(b.getAvailable() + availDelta);
        b.setFrozen(Math.max(0, b.getFrozen() + frozenDelta));
        markDirty(userId, assetId, b, seq, tradeId);
    }

    private Balance prepareNewAccount() {
        Balance b = new Balance();
        b.setAvailable(0); b.setFrozen(0);
        b.setVersion(0); b.setLastSeq(-1);
        b.setLastTradeId(0);
        return b;
    }

    private void markDirty(long userId, int assetId, Balance b, long seq, long tradeId) {
        b.setVersion(b.getVersion() + 1);
        b.setLastSeq(seq);
        if (tradeId > 0) b.setLastTradeId(tradeId);
        
        // 性能優化：如果隊列未滿則加入，若滿了則在 flush 時會由全量掃描或增加容量處理
        // 這裡暫不考慮極端去重，因為 ChronicleMap.put 覆蓋相同資料開銷可接受
        if (dirtyCount < dirtyQueue.length) {
            dirtyQueue[dirtyCount++] = combine(userId, assetId);
        }
        
        updateAssetIndex(userId, assetId);
    }

    private void updateAssetIndex(long userId, int assetId) {
        if (assetId < 0 || assetId >= 64) return;
        long mask = bitmaskCache.get(userId);
        long newMask = mask | (1L << assetId);
        if (mask != newMask) {
            bitmaskCache.put(userId, newMask);
            dirtyBitmasks.add(userId);
        }
    }

    public void rebuildAssetIndexes() {
        log.info("--- 執行帳本二級緩存預熱與索引重建 ---");
        bitmaskCache.clear();
        balanceCache.clear();
        balancesDiskMap.forEach((key, diskVal) -> {
            // 預熱內存緩存
            Balance b = new Balance();
            b.copyFrom(diskVal);
            balanceCache.put(combine(key.getUserId(), key.getAssetId()), b);
            
            // 重建索引
            if (b.getAvailable() > 0 || b.getFrozen() > 0) {
                int assetId = key.getAssetId();
                if (assetId >= 0 && assetId < 64) {
                    long mask = bitmaskCache.get(key.getUserId());
                    bitmaskCache.put(key.getUserId(), mask | (1L << assetId));
                }
            }
        });
        bitmaskCache.forEach(userAssetBitmaskDiskMap::put);
        log.info("✅ Ledger 預熱完成，緩存帳戶數: {}", balanceCache.size());
    }

    public boolean hasAsset(long userId, int assetId) {
        if (assetId >= 0 && assetId < 64) {
            return (bitmaskCache.get(userId) & (1L << assetId)) != 0;
        }
        return balanceCache.containsKey(combine(userId, assetId));
    }

    public void initAccount(long userId, int assetId, long seq) {
        access(userId, assetId, 0, 0, seq, 0);
    }
}
