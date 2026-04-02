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
 撮合引擎核心 (Matching Engine)
 職責：指令分發與進度持久化。
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

    public void onStart() {
        log.info("執行冷啟動最小重建，後續由 WAL 重放收斂狀態...");
        unflushedWorkCount = 0;
        WalProgress savedWal = walMetadata.get(MetaDataKey.Wal.MACHING_ENGINE_POINT);
        if (savedWal != null) progress.copyFrom(savedWal);
        
        MsgProgress savedMsg = msgMetadata.get(MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE);
        if (savedMsg != null) networkProgress.copyFrom(savedMsg);
        
        pendingFlushSeq = MSG_SEQ_NONE;

        ledger.rebuildAssetIndexes();
        long maxOrderId = orderProcessor.coldStartRebuild();
        progress.alignNextIds(maxOrderId, rebuildTradeCounterFloor());

        coreStateValidator.validateOnRecovery();
        OrderBook.get(1001);
        log.info(
            "Engine 啟動完成，durableSeq={}, nextOrderId={}, nextTradeId={}",
            progress.getLastProcessedMsgSeq(), progress.getOrderIdCounter(), progress.getTradeIdCounter()
        );
    }

    public void onPollCycle(int done, long latestSeq) {
        if (done > 0) {
            unflushedWorkCount += done;
            lastReceivedSeq = latestSeq;
        }

        final long now = Clock.now();
        if (unflushedWorkCount > 0 && (unflushedWorkCount >= 100000 || now - lastFlushTime >= 1000)) {
            flushAll();
            lastFlushTime = now;
            unflushedWorkCount = 0;
        }
    }

    private void flushAll() {
        orderProcessor.flush();
        for (OrderBook book : OrderBook.getInstances()) book.flush();
        ledger.flush();
        
        // 1. 持久化業務進度 (WalProgress)
        if (pendingFlushSeq != MSG_SEQ_NONE) {
            progress.commitLastProcessedMsgSeq(pendingFlushSeq);
            pendingFlushSeq = MSG_SEQ_NONE;
        }
        walMetadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
        
        // 2. 持久化網路接收進度 (MsgProgress)
        if (lastReceivedSeq != MSG_SEQ_NONE) {
            networkProgress.setLastProcessedSeq(lastReceivedSeq);
            msgMetadata.put(MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE, networkProgress);
        }
    }

    public void onAeronMessage(int msgType, org.agrona.DirectBuffer buffer, int offset, int length) {
        final long arrivalTimeNs = System.nanoTime();
        final long gatewayTimeNs = buffer.getLong(offset + 16, java.nio.ByteOrder.LITTLE_ENDIAN);

        long seq = router.route(msgType, buffer, offset, length, gatewayTimeNs, progress);

        final long endNs = System.nanoTime();
        recordMessageMetrics(msgType, arrivalTimeNs, gatewayTimeNs, endNs);
        if (seq != MSG_SEQ_NONE) {
            long previousSeq = pendingFlushSeq != MSG_SEQ_NONE ? pendingFlushSeq : progress.getLastProcessedMsgSeq();
            if (previousSeq != MSG_SEQ_NONE && seq <= previousSeq) {
                throw new IllegalStateException(
                    "Pending message sequence must advance monotonically, last=%d, actual=%d"
                        .formatted(previousSeq, seq)
                );
            }
            pendingFlushSeq = seq;
        }
    }

    private void recordMessageMetrics(int msgType, long arrivalTimeNs, long gatewayTimeNs, long endNs) {
        if (msgType != MsgType.ORDER_CREATE && msgType != MsgType.ORDER_CANCEL) return;
        StaticMetricsHolder.addCounter(MetricsKey.ORDER_PROCESSED_COUNT, 1);
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_TRANSPORT, arrivalTimeNs - gatewayTimeNs);
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_MATCHING, endNs - arrivalTimeNs);
    }

    public void onStop() {
        flushAll();
        reporter.close();
        ThreadContext.cleanup();
    }

    private long rebuildTradeCounterFloor() {
        final long[] maxTradeId = new long[1];
        Storage.self().trades().forEach((tradeIdKey, trade) -> {
            if (trade != null) maxTradeId[0] = Math.max(maxTradeId[0], trade.getTradeId());
        });
        return maxTradeId[0];
    }
}
