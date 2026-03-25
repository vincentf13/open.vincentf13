package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.MetricsCollector;
import open.vincentf13.service.spot.model.WalProgress;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver.AeronMessageHandler;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 撮合引擎核心 (Matching Engine)
 職責：指令分發與進度持久化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Engine implements AeronMessageHandler {
    private final ChronicleMap<Byte, WalProgress> metadata = Storage.self().walMetadata();
    private final CommandRouter router;
    private final OrderProcessor orderProcessor;
    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    private final WalProgress progress = new WalProgress();

    private long lastMetricsSec = 0;
    private long lastFlushTime = 0;
    private long unflushedWorkCount = 0;

    public void onStart() {
        log.info("執行冷啟動索引重建與預熱...");
        unflushedWorkCount = 0;
        
        ledger.rebuildAssetIndexes();
        OrderBook.rebuildActiveOrdersIndexes();
        orderProcessor.coldStartRebuild();
        OrderBook.get(1001); 

        WalProgress saved = metadata.get(MetaDataKey.Wal.MACHING_ENGINE_POINT);
        if (saved != null) progress.copyFrom(saved);
        log.info("Engine 啟動完成，位點: Seq={}", progress.getLastProcessedMsgSeq());
    }

    public void tick(int done) {
        if (done > 0) { unflushedWorkCount += done; }

        final long now = open.vincentf13.service.spot.infra.util.Clock.now();
        if (unflushedWorkCount > 0 && (unflushedWorkCount >= 100000 || now - lastFlushTime >= 1000)) {
            flushAll(); lastFlushTime = now; unflushedWorkCount = 0;
        }

        final long nowSec = now / 1000;
        if (nowSec > lastMetricsSec) { updateSystemMetrics(nowSec); lastMetricsSec = nowSec; }
    }

    private void flushAll() {
        ledger.flush(); orderProcessor.flush();
        for (OrderBook book : OrderBook.getInstances()) book.flush();
        metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
    }

    @Override
    public void onMessage(int msgType, org.agrona.DirectBuffer buffer, int offset, int length) {
        final long gatewayTime = buffer.getLong(offset + 12);
        long seq = router.route(msgType, buffer, offset, length, gatewayTime, progress);
        if (seq != MSG_SEQ_NONE) {
            long last = progress.getLastProcessedMsgSeq();
            if (last != MSG_SEQ_NONE && seq != last + 1) {
                log.error("❌ [FATAL] 指令跳號！期望: {}, 實際: {}", last + 1, seq);
                System.exit(1); 
            }
            progress.setLastProcessedMsgSeq(seq);
        }
    }

    private void updateSystemMetrics(long nowSec) {
        // 僅回報業務相關指標
        MetricsCollector.set(MetricsKey.MATCH_COUNT, OrderBook.TOTAL_MATCH_COUNT.get());
    }

    public void onStop() { 
        metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
        reporter.close(); ThreadContext.cleanup(); 
    }
}
