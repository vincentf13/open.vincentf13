package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.model.WalProgress;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 撮合引擎核心 (Matching Engine)
 職責：指令分發與進度持久化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Engine {
    private final ChronicleMap<Byte, WalProgress> metadata = Storage.self().walMetadata();
    private final CommandRouter router;
    private final OrderProcessor orderProcessor;
    private final Ledger ledger;
    private final ExecutionReporter reporter;
    private final CoreStateValidator coreStateValidator;
    
    private final WalProgress progress = new WalProgress();

    private long lastFlushTime = 0;
    private long unflushedWorkCount = 0;
    private long pendingFlushSeq = MSG_SEQ_NONE;

    public void onStart() {
        log.info("執行冷啟動最小重建，後續由 WAL 重放收斂狀態...");
        unflushedWorkCount = 0;
        restoreRuntimeProgress();
        rebuildRuntimeState();
        coreStateValidator.validateOnRecovery();
        OrderBook.get(1001); 
        log.info(
            "Engine 啟動完成，durableSeq={}, nextOrderId={}, nextTradeId={}",
            progress.getLastProcessedMsgSeq(),
            progress.getOrderIdCounter(),
            progress.getTradeIdCounter()
        );
    }

    private void restoreRuntimeProgress() {
        WalProgress saved = metadata.get(MetaDataKey.Wal.MACHING_ENGINE_POINT);
        if (saved != null) {
            progress.copyFrom(saved);
        }
        pendingFlushSeq = MSG_SEQ_NONE;
    }

    private void rebuildRuntimeState() {
        ledger.rebuildAssetIndexes();
        long maxOrderId = orderProcessor.coldStartRebuild();
        progress.alignNextIds(maxOrderId, rebuildTradeCounterFloor());
    }

    public void onPollCycle(int done) {
        if (done > 0) { unflushedWorkCount += done; }

        final long now = open.vincentf13.service.spot.infra.util.Clock.now();
        if (unflushedWorkCount > 0 && (unflushedWorkCount >= 100000 || now - lastFlushTime >= 1000)) {
            flushDurableState();
            lastFlushTime = now;
            unflushedWorkCount = 0;
        }
    }

    private void flushDurableState() {
        flushOrderState();
        ledger.flush();
        if (pendingFlushSeq != MSG_SEQ_NONE) {
            progress.commitLastProcessedMsgSeq(pendingFlushSeq);
            pendingFlushSeq = MSG_SEQ_NONE;
        }
        persistCheckpoint();
    }

    private void flushOrderState() {
        orderProcessor.flush();
        for (OrderBook book : OrderBook.getInstances()) {
            book.flush();
        }
    }

    private void persistCheckpoint() {
        metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
    }

    public void onAeronMessage(int msgType, org.agrona.DirectBuffer buffer, int offset, int length) {
        final long arrivalTimeNs = System.nanoTime();
        final long gatewayTimeNs = buffer.getLong(offset + 12);
        
        long seq = router.route(msgType, buffer, offset, length, gatewayTimeNs, progress);
        
        final long endNs = System.nanoTime();
        recordMessageMetrics(msgType, arrivalTimeNs, gatewayTimeNs, endNs);
        if (seq != MSG_SEQ_NONE) {
            recordPendingSeq(seq);
        }
    }

    private void recordPendingSeq(long seq) {
        if (seq == MSG_SEQ_NONE) return;
        long previousSeq = pendingFlushSeq != MSG_SEQ_NONE ? pendingFlushSeq : progress.getLastProcessedMsgSeq();
        if (previousSeq != MSG_SEQ_NONE && seq != previousSeq + 1) {
            throw new IllegalStateException(
                "Pending message sequence must advance monotonically, expected=%d, actual=%d"
                    .formatted(previousSeq + 1, seq)
            );
        }
        pendingFlushSeq = seq;
    }

    private void recordMessageMetrics(int msgType, long arrivalTimeNs, long gatewayTimeNs, long endNs) {
        if (msgType != MsgType.ORDER_CREATE && msgType != MsgType.ORDER_CANCEL) {
            return;
        }

        StaticMetricsHolder.addCounter(MetricsKey.ORDER_PROCESSED_COUNT, 1);
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_TRANSPORT, arrivalTimeNs - gatewayTimeNs);
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_MATCHING, endNs - arrivalTimeNs);
    }

    public void onStop() {
        flushDurableState();
        reporter.close();
        ThreadContext.cleanup();
    }

    private long rebuildTradeCounterFloor() {
        final long[] maxTradeId = new long[1];
        Storage.self().trades().forEach((tradeIdKey, trade) -> {
            if (trade != null) {
                maxTradeId[0] = Math.max(maxTradeId[0], trade.getTradeId());
            }
        });
        return maxTradeId[0];
    }
}

