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

    private final Balance reusableBalance = new Balance();
    private final BalanceKey reusableKey = new BalanceKey();
    private final Balance coldStartReusable = new Balance();

    @PostConstruct
    public void init() {
        log.info("Ledger 開始預加載用戶資產位元遮罩...");
        userAssetBitmaskDiskMap.forEach(bitmaskCache::put);
        log.info("預加載完成，共載入 {} 筆記錄", bitmaskCache.size());
    }

    public void settleTrade(long mUid, long tUid, long tradePrice, long tradeQty, byte takerSide, long takerPrice, long seq, int baseAssetId, int quoteAssetId, long tradeId) {
        // 安全預檢
        if (!DecimalUtil.isMultiplySafe(tradePrice, tradeQty)) {
            throw new ArithmeticException("成交金額溢出！Price: " + tradePrice + ", Qty: " + tradeQty);
        }

        final long floor = DecimalUtil.mulFloor(tradePrice, tradeQty), ceil = DecimalUtil.mulCeil(tradePrice, tradeQty);

        if (takerSide == OrderSide.BUY) {
            final long takerFrozenTotal = DecimalUtil.mulCeil(takerPrice, tradeQty);
            // 關鍵：使用 tradeId 作為更精確的冪等屏障
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
        access(userId, assetId, amount, 0, seq, 0); // 存款非成交，tradeId 填 0
    }

    public boolean freezeBalance(long userId, int assetId, long amount, long seq) {
        reusableKey.set(userId, assetId);
        Balance b = balancesDiskMap.getUsing(reusableKey, reusableBalance);
        if (b == null) b = prepareNewAccount();
        
        // 指令級屏障：風控扣款在一筆指令中只會發生一次
        if (b.getLastSeq() >= seq) return true;

        if (b.getAvailable() >= amount) {
            b.setAvailable(b.getAvailable() - amount);
            b.setFrozen(b.getFrozen() + amount);
            commit(reusableKey, b, seq, 0);
            return true;
        }
        return false;
    }

    public void initAccount(long userId, int assetId, long seq) {
        access(userId, assetId, 0, 0, seq, 0);
    }

    private void access(long userId, int assetId, long availDelta, long frozenDelta, long seq, long tradeId) {
        reusableKey.set(userId, assetId);
        Balance b = balancesDiskMap.getUsing(reusableKey, reusableBalance);
        if (b == null) b = prepareNewAccount();

        // 雙重屏障邏輯：
        // 1. 如果是純指令更新（非成交），校驗 lastSeq
        // 2. 如果是成交觸發，校驗 lastTradeId (tradeId 全域唯一遞增)
        if (tradeId > 0) {
            if (b.getLastTradeId() >= tradeId) return;
        } else {
            if (b.getLastSeq() >= seq && availDelta == 0 && frozenDelta == 0) return; // 僅針對 init/noop 跳過
        }

        b.setAvailable(b.getAvailable() + availDelta);
        b.setFrozen(Math.max(0, b.getFrozen() + frozenDelta));
        commit(reusableKey, b, seq, tradeId);
    }

    private Balance prepareNewAccount() {
        coldStartReusable.setAvailable(0); coldStartReusable.setFrozen(0);
        coldStartReusable.setVersion(0); coldStartReusable.setLastSeq(-1);
        coldStartReusable.setLastTradeId(0);
        return coldStartReusable;
    }

    private void commit(BalanceKey key, Balance b, long seq, long tradeId) {
        b.setVersion(b.getVersion() + 1);
        b.setLastSeq(seq);
        if (tradeId > 0) b.setLastTradeId(tradeId);
        balancesDiskMap.put(key, b);
        updateAssetIndex(key.getUserId(), key.getAssetId());
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
