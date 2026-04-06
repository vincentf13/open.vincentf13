package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.model.MsgProgress;
import open.vincentf13.service.spot.model.WalProgress;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 撮合引擎核心 (Matching Engine)
 *
 * 職責：運行時指令分發、flush 編排、進度持久化。
 * 冷啟動與 JIT 預熱委派給 {@link EngineRecovery}。
 *
 * 全非同步 Flush：
 * - matching thread 只做 rotate() 指針翻轉（ns 級）
 * - AsyncDiskFlusher 接手所有 ChronicleMap.put（Orders/Trades/Idempotency/Balance/Bitmask/Progress）
 * - 目標：matching 端到端延遲 µs 級，無 disk I/O 阻塞
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Engine {
    private final ChronicleMap<Byte, WalProgress> walMetadata = Storage.self().walMetadata();
    private final ChronicleMap<Byte, MsgProgress> msgMetadata = Storage.self().msgProgressMetadata();
    private final CommandRouter router;
    private final OrderProcessor orderProcessor;
    private final Ledger ledger;
    private final ExecutionReporter reporter;
    private final CoreStateValidator coreStateValidator;
    private final AsyncDiskFlusher diskFlusher;

    private final WalProgress progress = new WalProgress();
    @Getter private final MsgProgress networkProgress = new MsgProgress();

    // Progress snapshot（matching 填入，flusher 讀出寫盤）
    private final WalProgress progressSnap = new WalProgress();
    private final MsgProgress netProgressSnap = new MsgProgress();
    private volatile boolean progressReady = false;

    private long pendingFlushSeq = MSG_SEQ_NONE;
    private long lastReceivedSeq = MSG_SEQ_NONE;

    // ========== 生命週期 ==========

    @PostConstruct
    public void registerDiskSinks() {
        diskFlusher.register(orderProcessor.getIdempotencyGuard());
        diskFlusher.register(new OrderBookDiskSink());
        diskFlusher.register(ledger);
        diskFlusher.register(new ProgressDiskSink());
    }

    public void onStart() {
        pendingFlushSeq = MSG_SEQ_NONE;
        reporter.init();
        EngineRecovery.recover(progress, networkProgress, orderProcessor, ledger, coreStateValidator);
    }

    public void onStop() {
        rotateAll();
        prepareProgressSnap();  // 最後一次 snapshot，讓 flusher 最終排空時寫出
        reporter.close();
        ThreadContext.cleanup();
    }

    // ========== 運行時 ==========

    public void onAeronMessage(int msgType, org.agrona.DirectBuffer buffer, int offset, int length) {
        final long arrivalTimeNs = System.nanoTime();
        final long gatewayTimeNs = buffer.getLong(offset + 16, java.nio.ByteOrder.LITTLE_ENDIAN);

        long seq = router.route(msgType, buffer, offset, length, gatewayTimeNs, progress);

        // 使用最後一個 report 的 writeFrameHeader nanoTime 作為 matching 結束時間
        // 確保 matching 和 report_delivery 在同一個時間點切分，緊貼不交疊
        recordMessageMetrics(msgType, arrivalTimeNs, gatewayTimeNs, reporter.getMatchingEndNs());
        if (seq != MSG_SEQ_NONE) {
            long previousSeq = pendingFlushSeq != MSG_SEQ_NONE ? pendingFlushSeq : progress.getLastProcessedMsgSeq();
            if (previousSeq != MSG_SEQ_NONE && seq <= previousSeq) {
                throw new IllegalStateException("Sequence must advance, last=%d, actual=%d".formatted(previousSeq, seq));
            }
            pendingFlushSeq = seq;
        }
    }

    public void onPollCycle(int done, long latestSeq) {
        if (done > 0) lastReceivedSeq = latestSeq;
        // 每輪都翻轉（指針操作 ns 級）+ 準備 progress 快照
        rotateAll();
        prepareProgressSnap();
    }

    // ========== 內部 ==========

    private void rotateAll() {
        orderProcessor.getIdempotencyGuard().rotate();
        for (OrderBook book : OrderBook.getInstances()) book.rotate();
        ledger.rotate();
    }

    /** matching thread 呼叫：若 flusher 已寫完上一輪 progress，copy 當前 progress 給 flusher */
    private void prepareProgressSnap() {
        if (progressReady) return; // flusher 尚未寫完上一輪
        if (pendingFlushSeq != MSG_SEQ_NONE) {
            progress.commitLastProcessedMsgSeq(pendingFlushSeq);
            pendingFlushSeq = MSG_SEQ_NONE;
        }
        if (lastReceivedSeq != MSG_SEQ_NONE) {
            networkProgress.setLastProcessedSeq(lastReceivedSeq);
        }
        progressSnap.copyFrom(progress);
        netProgressSnap.copyFrom(networkProgress);
        progressReady = true;  // volatile 發布快照
    }

    private void recordMessageMetrics(int msgType, long arrivalTimeNs, long gatewayTimeNs, long endNs) {
        if (msgType != MsgType.ORDER_CREATE && msgType != MsgType.ORDER_CANCEL) return;
        StaticMetricsHolder.addCounter(MetricsKey.ORDER_PROCESSED_COUNT, 1);
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_TRANSPORT, arrivalTimeNs - gatewayTimeNs);
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_MATCHING, endNs - arrivalTimeNs);
    }

    /** 遍歷所有 OrderBook 實例執行 drainToDisk，由 AsyncDiskFlusher 呼叫 */
    private static class OrderBookDiskSink implements DiskSink {
        @Override public boolean rotate() { return false; }
        @Override public void drainToDisk() {
            for (OrderBook book : OrderBook.getInstances()) book.drainToDisk();
        }
    }

    /** Progress/NetworkProgress 快照寫入 ChronicleMap，由 AsyncDiskFlusher 呼叫 */
    private class ProgressDiskSink implements DiskSink {
        @Override public boolean rotate() { return false; }
        @Override public void drainToDisk() {
            if (!progressReady) return;
            walMetadata.put(MetaDataKey.Wal.MATCHING_ENGINE_POINT, progressSnap);
            msgMetadata.put(MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE, netProgressSnap);
            progressReady = false;  // 釋放給 matching 準備下一輪快照
        }
    }
}
