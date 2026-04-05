package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.util.Clock;
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
 * Flush 策略：
 * - 大量 ChronicleMap.put (Orders/Trades/ActiveOrders/Idempotency) 移至 {@link AsyncDiskFlusher}
 * - matching thread 只負責：rotate() 指針翻轉（ns 級）+ reclaimPool() 回收終局物件
 * - Ledger + metadata 體積小（每輪 ~2 puts），繼續在 matching thread 做，每 50ms 一次
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Engine {
    private static final long METADATA_FLUSH_INTERVAL_MS = 50;

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

    private long lastMetadataFlushTime = 0;
    private long pendingFlushSeq = MSG_SEQ_NONE;
    private long lastReceivedSeq = MSG_SEQ_NONE;

    // ========== 生命週期 ==========

    @PostConstruct
    public void registerDiskSinks() {
        diskFlusher.register(orderProcessor.getIdempotencyGuard());
        diskFlusher.register(new OrderBookDiskSink());
    }

    public void onStart() {
        pendingFlushSeq = MSG_SEQ_NONE;
        reporter.init();
        EngineRecovery.recover(progress, networkProgress, orderProcessor, ledger, coreStateValidator);
    }

    public void onStop() {
        rotateAllSinks();
        // AsyncDiskFlusher 的 @PreDestroy 會做最終排空
        ledger.flush();
        persistMetadata();
        reporter.close();
        ThreadContext.cleanup();
    }

    // ========== 運行時 ==========

    public void onAeronMessage(int msgType, org.agrona.DirectBuffer buffer, int offset, int length) {
        final long arrivalTimeNs = System.nanoTime();
        final long gatewayTimeNs = buffer.getLong(offset + 16, java.nio.ByteOrder.LITTLE_ENDIAN);

        long seq = router.route(msgType, buffer, offset, length, gatewayTimeNs, progress);

        recordMessageMetrics(msgType, arrivalTimeNs, gatewayTimeNs, System.nanoTime());
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
        // 每輪都翻轉（操作廉價，只做指針翻轉）
        rotateAllSinks();
        // metadata + ledger 仍同步，但體積小（50ms 一次）
        long now = Clock.now();
        if (now - lastMetadataFlushTime >= METADATA_FLUSH_INTERVAL_MS) {
            ledger.flush();
            persistMetadata();
            lastMetadataFlushTime = now;
        }
    }

    // ========== 內部 ==========

    private void rotateAllSinks() {
        orderProcessor.getIdempotencyGuard().rotate();
        for (OrderBook book : OrderBook.getInstances()) book.rotate();
    }

    private void persistMetadata() {
        if (pendingFlushSeq != MSG_SEQ_NONE) {
            progress.commitLastProcessedMsgSeq(pendingFlushSeq);
            pendingFlushSeq = MSG_SEQ_NONE;
        }
        walMetadata.put(MetaDataKey.Wal.MATCHING_ENGINE_POINT, progress);
        if (lastReceivedSeq != MSG_SEQ_NONE) {
            networkProgress.setLastProcessedSeq(lastReceivedSeq);
            msgMetadata.put(MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE, networkProgress);
        }
    }

    private void recordMessageMetrics(int msgType, long arrivalTimeNs, long gatewayTimeNs, long endNs) {
        if (msgType != MsgType.ORDER_CREATE && msgType != MsgType.ORDER_CANCEL) return;
        StaticMetricsHolder.addCounter(MetricsKey.ORDER_PROCESSED_COUNT, 1);
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_TRANSPORT, arrivalTimeNs - gatewayTimeNs);
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_MATCHING, endNs - arrivalTimeNs);
    }

    /** 遍歷所有 OrderBook 實例執行 drainToDisk，由 AsyncDiskFlusher 呼叫 */
    private static class OrderBookDiskSink implements DiskSink {
        @Override
        public boolean rotate() { return false; }  // rotate 仍由 matching thread 呼叫
        @Override
        public void drainToDisk() {
            for (OrderBook book : OrderBook.getInstances()) book.drainToDisk();
        }
    }
}
