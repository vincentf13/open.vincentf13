package open.vincentf13.service.spot.matching.engine;

import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.LongValue;
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

import java.util.HashMap;
import java.util.Map;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 內存帳務處理器 (Ledger) - 零對象分配 & 二級緩存版
 * 職責：管理資產流轉，使用內存緩存徹底消除熱點帳戶的磁碟 I/O 與對象裝箱。
 */
@Slf4j
@Service
public class Ledger {
    private final ChronicleMap<BalanceKey, Balance> balancesDiskMap = Storage.self().balances();
    private final ChronicleMap<LongValue, LongValue> userAssetBitmaskDiskMap = Storage.self().userAssets();

    // 二級緩存：徹底消除 BalanceKey 對象分配與磁碟讀取
    private final Long2ObjectHashMap<Balance> balanceCache = new Long2ObjectHashMap<>(100_000, 0.5f);
    private final Long2LongHashMap bitmaskCache = new Long2LongHashMap(100_000, 0.5f, 0L);

    // 待落地標記優化：唯一標記去重版
    private static final int MAX_DIRTY = 256000;
    private final long[] dirtyQueue = new long[MAX_DIRTY];
    private int dirtyCount = 0;
    private final LongHashSet dirtyBitmasks = new LongHashSet(1000);

    private final BalanceKey reusableKey = new BalanceKey();
    private final Balance reusableDiskBalance = new Balance(); // getUsing() 享元，避免 ChronicleMap.get() 分配
    private final LongValue flushMaskKey = new LongValue();
    private final LongValue flushMaskValue = new LongValue();

    @PostConstruct
    public void init() {
        log.info("Ledger 正在預加載帳務數據至二級緩存...");
        userAssetBitmaskDiskMap.forEach((k, v) -> bitmaskCache.put(k.getValue(), v.getValue()));
        log.info("Ledger 初始化完成。");
    }

    public void flush() {
        if (dirtyCount > 0) {
            for (int i = 0; i < dirtyCount; i++) {
                final long combinedKey = dirtyQueue[i];
                final Balance b = balanceCache.get(combinedKey);
                if (b != null) {
                    reusableKey.set(combinedKey >>> 32, (int) (combinedKey & 0xFFFFFFFFL));
                    balancesDiskMap.put(reusableKey, b);
                    b.setDirty(false);
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
            Balance diskVal = balancesDiskMap.getUsing(reusableKey, reusableDiskBalance);
            if (diskVal != null) {
                b = new Balance();
                b.copyFrom(reusableDiskBalance);
            } else {
                b = new Balance();
                b.setLastSeq(-1);
            }
            balanceCache.put(combinedKey, b);
        }
        return b;
    }

    public void settleTrade(long mUid, long tUid, long tradePrice, long tradeQty, byte takerSide, long mFrozenDelta, long tFrozenDelta, long seq, int baseAssetId, int quoteAssetId, long tradeId) {
        if (tradePrice <= 0 || tradeQty <= 0) {
            throw new IllegalArgumentException("Invalid trade payload, price=%d, qty=%d".formatted(tradePrice, tradeQty));
        }
        if (tradeId <= 0) {
            throw new IllegalArgumentException("Trade settlement requires positive tradeId");
        }

        final long floor = DecimalUtil.mulFloor(tradePrice, tradeQty), ceil = DecimalUtil.mulCeil(tradePrice, tradeQty);

        if (takerSide == OrderSide.BUY) {
            applyTradeChange(tUid, quoteAssetId, tFrozenDelta - ceil, -tFrozenDelta, seq, tradeId);
            applyTradeChange(tUid, baseAssetId, tradeQty, 0, seq, tradeId);
            applyTradeChange(mUid, baseAssetId, 0, -tradeQty, seq, tradeId);
            applyTradeChange(mUid, quoteAssetId, floor, 0, seq, tradeId);
        } else {
            applyTradeChange(tUid, baseAssetId, 0, -tFrozenDelta, seq, tradeId);
            applyTradeChange(tUid, quoteAssetId, floor, 0, seq, tradeId);
            applyTradeChange(mUid, quoteAssetId, mFrozenDelta - ceil, -mFrozenDelta, seq, tradeId);
            applyTradeChange(mUid, baseAssetId, tradeQty, 0, seq, tradeId);
        }
        if (ceil > floor) {
            applyTradeChange(PLATFORM_USER_ID, quoteAssetId, ceil - floor, 0, seq, tradeId);
        }
    }

    public void increaseAvailable(long userId, int assetId, long amount, long seq) {
        if (amount <= 0) return;
        applySeqChange(userId, assetId, amount, 0, seq);
    }

    public boolean freezeBalance(long userId, int assetId, long amount, long seq) {
        if (amount <= 0) return true; // 零凍結視為成功但不操作
        Balance b = getOrCreateBalance(userId, assetId);
        if (shouldSkipSeqChange(b, seq)) return true;
        if (b.getAvailable() < amount) return false;

        b.setAvailable(b.getAvailable() - amount);
        b.setFrozen(b.getFrozen() + amount);
        markDirtyForSeq(userId, assetId, b, seq);
        return true;
    }

    public void unfreezeBalance(long userId, int assetId, long amount, long seq) {
        if (amount <= 0) return; // 零解凍不操作
        Balance b = getOrCreateBalance(userId, assetId);
        if (shouldSkipSeqChange(b, seq)) return;
        if (amount > b.getFrozen()) {
            throw new IllegalStateException(
                "Unfreeze exceeds frozen balance, uid=%d, asset=%d, frozen=%d, amount=%d, seq=%d"
                    .formatted(userId, assetId, b.getFrozen(), amount, seq)
            );
        }

        b.setAvailable(b.getAvailable() + amount);
        b.setFrozen(b.getFrozen() - amount);
        markDirtyForSeq(userId, assetId, b, seq);
    }

    private void applySeqChange(long userId, int assetId, long availDelta, long frozenDelta, long seq) {
        Balance b = getOrCreateBalance(userId, assetId);
        if (shouldSkipSeqChange(b, seq)) return;
        applyBalanceDelta(userId, assetId, b, availDelta, frozenDelta, seq, 0);
        markDirtyForSeq(userId, assetId, b, seq);
    }

    private void applyTradeChange(long userId, int assetId, long availDelta, long frozenDelta, long seq, long tradeId) {
        Balance b = getOrCreateBalance(userId, assetId);
        if (shouldSkipTradeChange(b, tradeId)) return;
        if (seq < b.getLastSeq()) {
            throw new IllegalStateException(
                "Trade sequence regressed, uid=%d, asset=%d, balanceSeq=%d, tradeSeq=%d, tradeId=%d"
                    .formatted(userId, assetId, b.getLastSeq(), seq, tradeId)
            );
        }
        applyBalanceDelta(userId, assetId, b, availDelta, frozenDelta, seq, tradeId);
        markDirtyForTrade(userId, assetId, b, seq, tradeId);
    }

    private void applyBalanceDelta(long userId, int assetId, Balance b, long availDelta, long frozenDelta, long seq, long tradeId) {
        long nextAvailable = b.getAvailable() + availDelta;
        long nextFrozen = b.getFrozen() + frozenDelta;
        if (nextAvailable < 0 || nextFrozen < 0) {
            throw new IllegalStateException(
                "Negative balance state, uid=%d, asset=%d, avail=%d, frozen=%d, seq=%d, tradeId=%d"
                    .formatted(userId, assetId, nextAvailable, nextFrozen, seq, tradeId)
            );
        }
        b.setAvailable(nextAvailable);
        b.setFrozen(nextFrozen);
    }

    private boolean shouldSkipSeqChange(Balance b, long seq) {
        return b.getLastSeq() >= seq;
    }

    private boolean shouldSkipTradeChange(Balance b, long tradeId) {
        return b.getLastTradeId() >= tradeId;
    }

    private void markDirtyForSeq(long userId, int assetId, Balance b, long seq) {
        b.setVersion(b.getVersion() + 1);
        b.setLastSeq(seq);
        enqueueDirty(userId, assetId, b);
        updateAssetIndex(userId, assetId);
    }

    private void markDirtyForTrade(long userId, int assetId, Balance b, long seq, long tradeId) {
        b.setVersion(b.getVersion() + 1);
        if (seq > b.getLastSeq()) b.setLastSeq(seq);
        b.setLastTradeId(tradeId);
        enqueueDirty(userId, assetId, b);
        updateAssetIndex(userId, assetId);
    }

    private void enqueueDirty(long userId, int assetId, Balance b) {
        if (!b.isDirty()) {
            if (dirtyCount >= MAX_DIRTY) {
                log.warn("[LEDGER] Dirty queue is full ({}), triggering emergency flush to disk...", MAX_DIRTY);
                flush();
            }
            dirtyQueue[dirtyCount++] = combine(userId, assetId);
            b.setDirty(true);
        }
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
        dirtyCount = 0;
        dirtyBitmasks.clear();
        balancesDiskMap.forEach((key, diskVal) -> {
            Balance b = new Balance();
            b.copyFrom(diskVal);
            b.setDirty(false);
            balanceCache.put(combine(key.getUserId(), key.getAssetId()), b);

            if (b.getAvailable() > 0 || b.getFrozen() > 0) {
                int assetId = key.getAssetId();
                if (assetId >= 0 && assetId < 64) {
                    long mask = bitmaskCache.get(key.getUserId());
                    bitmaskCache.put(key.getUserId(), mask | (1L << assetId));
                }
            }
        });
        bitmaskCache.forEach((k, v) -> {
            flushMaskKey.set(k);
            flushMaskValue.set(v);
            userAssetBitmaskDiskMap.put(flushMaskKey, flushMaskValue);
        });
        log.info("Ledger 預熱完��，緩存帳戶數: {}", balanceCache.size());
    }

    public boolean hasAsset(long userId, int assetId) {
        if (assetId >= 0 && assetId < 64) {
            return (bitmaskCache.get(userId) & (1L << assetId)) != 0;
        }
        return balanceCache.containsKey(combine(userId, assetId));
    }

    public void initAccount(long userId, int assetId, long seq) {
        applySeqChange(userId, assetId, 0, 0, seq);
    }

    public void validateState() {
        Map<Long, Long> expectedMasks = new HashMap<>();
        balanceCache.forEach((combinedKey, balance) -> {
            if (balance.getAvailable() < 0 || balance.getFrozen() < 0) {
                throw new IllegalStateException("Negative balance cache state for key=" + combinedKey);
            }
            long userId = combinedKey >>> 32;
            int assetId = (int) (long) combinedKey;
            if ((balance.getAvailable() > 0 || balance.getFrozen() > 0) && assetId >= 0 && assetId < 64) {
                expectedMasks.merge(userId, 1L << assetId, (l, r) -> l | r);
            }
        });

        bitmaskCache.forEach((userId, mask) -> {
            long expected = expectedMasks.getOrDefault(userId, 0L);
            if (mask != expected) throw new IllegalStateException(
                "User asset bitmask mismatch, uid=%d, expected=%d, actual=%d".formatted(userId, expected, mask));
        });

        expectedMasks.forEach((userId, expected) -> {
            if (bitmaskCache.get(userId) != expected) throw new IllegalStateException(
                "Missing user asset bitmask, uid=%d, expected=%d".formatted(userId, expected));
        });
    }
}
