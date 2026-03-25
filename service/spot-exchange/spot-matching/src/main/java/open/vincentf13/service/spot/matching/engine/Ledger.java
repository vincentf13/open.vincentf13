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
    private final ChronicleMap<open.vincentf13.service.spot.infra.chronicle.LongValue, open.vincentf13.service.spot.infra.chronicle.LongValue> userAssetBitmaskDiskMap = Storage.self().userAssets();
    
    // 二級緩存：徹底消除 BalanceKey 對象分配與磁碟讀取
    private final Long2ObjectHashMap<Balance> balanceCache = new Long2ObjectHashMap<>(100_000, 0.5f);
    private final Long2LongHashMap bitmaskCache = new Long2LongHashMap(100_000, 0.5f, 0L);
    
    // 待落地標記優化：唯一標記去重版
    private static final int MAX_DIRTY = 256000; // 足以支撐單個 flush 週期內 25.6 萬個活躍用戶
    private final long[] dirtyQueue = new long[MAX_DIRTY];
    private int dirtyCount = 0;
    private final LongHashSet dirtyBitmasks = new LongHashSet(1000);
    private long overflowLogSample = 0;

    private final BalanceKey reusableKey = new BalanceKey();
    private final open.vincentf13.service.spot.infra.chronicle.LongValue flushMaskKey = new open.vincentf13.service.spot.infra.chronicle.LongValue();
    private final open.vincentf13.service.spot.infra.chronicle.LongValue flushMaskValue = new open.vincentf13.service.spot.infra.chronicle.LongValue();

    @PostConstruct
    public void init() {
        log.info("Ledger 正在預加載帳務數據至二級緩存...");
        userAssetBitmaskDiskMap.forEach((k, v) -> bitmaskCache.put(k.getValue(), v.getValue()));
        log.info("Ledger 初始化完成。");
    }

    /**
     * 將變動過的緩存帳戶同步至磁碟
     */
    public void flush() {
        if (dirtyCount > 0) {
            // 核心優化：此處 dirtyQueue 內保證每個 UserId-AssetId 唯一，極大減少 I/O
            for (int i = 0; i < dirtyCount; i++) {
                final long combinedKey = dirtyQueue[i];
                final Balance b = balanceCache.get(combinedKey);
                if (b != null) {
                    reusableKey.set(combinedKey >>> 32, (int) (combinedKey & 0xFFFFFFFFL));
                    balancesDiskMap.put(reusableKey, b);
                    b.setDirty(false); // 重設標記，允許下次變動時再次入隊
                }
            }
            dirtyCount = 0;
        }

        if (!dirtyBitmasks.isEmpty()) {
            org.agrona.collections.LongHashSet.LongIterator iter = dirtyBitmasks.iterator();
            while (iter.hasNext()) {
                long userId = iter.nextValue();
                flushMaskKey.set(userId);
                flushMaskValue.set(bitmaskCache.get(userId));
                userAssetBitmaskDiskMap.put(flushMaskKey, flushMaskValue);
            }
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

    public void settleTrade(long mUid, long tUid, long tradePrice, long tradeQty, byte takerSide, long mFrozenDelta, long tFrozenDelta, long seq, int baseAssetId, int quoteAssetId, long tradeId) {
        final long floor = DecimalUtil.mulFloor(tradePrice, tradeQty), ceil = DecimalUtil.mulCeil(tradePrice, tradeQty);

        if (takerSide == OrderSide.BUY) {
            // Taker 是買方：扣除 Taker 的凍結款項 (tFrozenDelta)，增加 Taker 的 Base，減少 Maker 的 Base，增加 Maker 的 Quote (floor)
            access(tUid, quoteAssetId, tFrozenDelta - ceil, -tFrozenDelta, seq, tradeId);
            access(tUid, baseAssetId, tradeQty, 0, seq, tradeId);
            access(mUid, baseAssetId, 0, -tradeQty, seq, tradeId);
            access(mUid, quoteAssetId, floor, 0, seq, tradeId);
        } else {
            // Taker 是賣方：扣除 Taker 的 Base (tFrozenDelta=tradeQty)，增加 Taker 的 Quote (floor)，減少 Maker 的 Quote (mFrozenDelta)，增加 Maker 的 Base (tradeQty)
            access(tUid, baseAssetId, 0, -tFrozenDelta, seq, tradeId);
            access(tUid, quoteAssetId, floor, 0, seq, tradeId);
            access(mUid, quoteAssetId, mFrozenDelta - ceil, -mFrozenDelta, seq, tradeId);
            access(mUid, baseAssetId, tradeQty, 0, seq, tradeId);
        }
        if (ceil > floor) access(PLATFORM_USER_ID, quoteAssetId, ceil - floor, 0, seq, tradeId);
    }

    public void increaseAvailable(long userId, int assetId, long amount, long seq) {
        access(userId, assetId, amount, 0, seq, 0);
    }

    public boolean freezeBalance(long userId, int assetId, long amount, long seq) {
        Balance b = getOrCreateBalance(userId, assetId);
        if (b.getLastSeq() >= seq || b.getAvailable() < amount) return b.getLastSeq() >= seq;

        b.setAvailable(b.getAvailable() - amount);
        b.setFrozen(b.getFrozen() + amount);
        markDirty(userId, assetId, b, seq, 0);
        return true;
    }

    public void unfreezeBalance(long userId, int assetId, long amount, long seq) {
        Balance b = getOrCreateBalance(userId, assetId);
        if (b.getLastSeq() < seq) {
            b.setAvailable(b.getAvailable() + amount);
            b.setFrozen(Math.max(0, b.getFrozen() - amount));
            markDirty(userId, assetId, b, seq, 0);
        }
    }

    private void access(long userId, int assetId, long availDelta, long frozenDelta, long seq, long tradeId) {
        Balance b = getOrCreateBalance(userId, assetId);

        // 冪等與重複更新檢查
        if (tradeId > 0 ? b.getLastTradeId() >= tradeId : (b.getLastSeq() >= seq && availDelta == 0 && frozenDelta == 0)) return;

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

        if (!b.isDirty()) { // 僅在尚未入隊時入隊，實現絕對去重
            if (dirtyCount >= MAX_DIRTY) {
                log.warn("[LEDGER] Dirty queue is full ({}), triggering emergency flush to disk...", MAX_DIRTY);
                flush(); // 隊列滿時立即觸發落地，清空隊列以接收新數據
            }
            
            dirtyQueue[dirtyCount++] = combine(userId, assetId);
            b.setDirty(true);
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
        bitmaskCache.forEach((k, v) -> userAssetBitmaskDiskMap.put(new open.vincentf13.service.spot.infra.chronicle.LongValue(k), new open.vincentf13.service.spot.infra.chronicle.LongValue(v)));
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
