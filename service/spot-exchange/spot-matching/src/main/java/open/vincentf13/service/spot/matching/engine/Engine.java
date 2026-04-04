package open.vincentf13.service.spot.matching.engine;

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

    private final WalProgress progress = new WalProgress();
    @Getter private final MsgProgress networkProgress = new MsgProgress();

    private long lastFlushTime = 0;
    private long unflushedWorkCount = 0;
    private long pendingFlushSeq = MSG_SEQ_NONE;
    private long lastReceivedSeq = MSG_SEQ_NONE;

    // ========== 生命週期 ==========

    public void onStart() {
        unflushedWorkCount = 0;
        pendingFlushSeq = MSG_SEQ_NONE;
        EngineRecovery.recover(progress, networkProgress, orderProcessor, ledger, coreStateValidator);
    }

    public void onStop() {
        flushAll();
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
        if (done > 0) { unflushedWorkCount += done; lastReceivedSeq = latestSeq; }
        long now = Clock.now();
        if (unflushedWorkCount > 0 && (unflushedWorkCount >= 100000 || now - lastFlushTime >= 1000)) {
            flushAll();
            lastFlushTime = now;
            unflushedWorkCount = 0;
        }
    }

    // ========== 內部 ==========

    private void flushAll() {
        orderProcessor.flush();
        for (OrderBook book : OrderBook.getInstances()) book.flush();
        ledger.flush();

        if (pendingFlushSeq != MSG_SEQ_NONE) {
            progress.commitLastProcessedMsgSeq(pendingFlushSeq);
            pendingFlushSeq = MSG_SEQ_NONE;
        }
        walMetadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);

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
}
