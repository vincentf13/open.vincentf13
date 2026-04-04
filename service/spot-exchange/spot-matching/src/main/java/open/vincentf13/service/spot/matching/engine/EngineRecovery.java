package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.*;

import static open.vincentf13.service.spot.infra.Constants.MatchingConfig;

/**
 * 引擎冷啟動與預熱 (Engine Recovery)
 *
 * 職責：從磁碟快照恢復狀態 + JIT 預熱。
 * 僅在啟動階段調用一次，與運行時邏輯分離。
 */
@Slf4j
public class EngineRecovery {

    /**
     * 冷啟動：恢復 WalProgress / MsgProgress，重建 Ledger 與 OrderBook，驗證一致性。
     */
    public static void recover(WalProgress progress, MsgProgress networkProgress,
                               OrderProcessor orderProcessor, Ledger ledger, CoreStateValidator validator) {
        log.info("執行冷啟動最小重建...");

        var walMetadata = Storage.self().walMetadata();
        var msgMetadata = Storage.self().msgProgressMetadata();

        WalProgress savedWal = walMetadata.get(open.vincentf13.service.spot.infra.Constants.MetaDataKey.Wal.MATCHING_ENGINE_POINT);
        if (savedWal != null) progress.copyFrom(savedWal);

        MsgProgress savedMsg = msgMetadata.get(open.vincentf13.service.spot.infra.Constants.MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE);
        if (savedMsg != null) networkProgress.copyFrom(savedMsg);

        ledger.rebuildAssetIndexes();
        long maxOrderId = orderProcessor.coldStartRebuild();
        progress.alignNextIds(maxOrderId, rebuildTradeCounterFloor());

        validator.validateOnRecovery();
        OrderBook.get(1001); // 確保預設交易對已初始化

        warmupJit();
        log.info("Engine 啟動完成，durableSeq={}, nextOrderId={}, nextTradeId={}",
                progress.getLastProcessedMsgSeq(), progress.getOrderIdCounter(), progress.getTradeIdCounter());
    }

    /**
     * JIT 預熱：觸發關鍵路徑 C2 編譯。
     */
    static void warmupJit() {
        final int iterations = MatchingConfig.STARTUP_PRE_ALLOCATE_COUNT;
        Order o = new Order();
        Trade t = new Trade();
        Balance b = new Balance();
        long sink = 0;

        for (int i = 1; i <= iterations; i++) {
            o.fill(i, i, 1001, 60000_00000000L, 1000L, (byte) (i & 1), i, System.nanoTime(), i, 60000_00000000L);
            sink += o.remainingQty();
            o.setFilled(500); o.setStatus((byte) 1);
            sink += o.remainingQty(); o.isTerminal(); o.isActive();

            b.setAvailable(1_000_000_000L); b.setFrozen(100_000_000L);
            b.setVersion(i); b.setLastSeq(i);
            sink += b.getAvailable() + b.getFrozen();

            t.setTradeId(i); t.setPrice(60000_00000000L); t.setQty(1000L);
            sink += t.getPrice();
        }
        if (sink == Long.MIN_VALUE) log.trace("warmup sink: {}", sink);
        log.info("JIT 預熱完成 ({} iterations)", iterations);
    }

    private static long rebuildTradeCounterFloor() {
        final long[] maxTradeId = new long[1];
        Storage.self().trades().forEach((k, trade) -> {
            if (trade != null) maxTradeId[0] = Math.max(maxTradeId[0], trade.getTradeId());
        });
        return maxTradeId[0];
    }

    private EngineRecovery() {}
}
