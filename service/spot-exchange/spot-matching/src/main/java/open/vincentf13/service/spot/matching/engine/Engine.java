package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.MetricsCollector;
import open.vincentf13.service.spot.model.WalProgress;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver.AeronMessageHandler;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 撮合引擎核心 (Matching Engine) - 智慧型落地優化版
 職責：極簡化的指令分發與進度持久化中心。
 現在由 AeronReceiver 驅動，不主動繼承 Worker。
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
    private long workCount = 0;
    private long unflushedWorkCount = 0;
    private long localPollCount = 0, localWorkCount = 0;
    private static final int METRICS_BATCH_SIZE = 5000;

    public void onBind(int cpuId) {
        MetricsCollector.recordCpuAffinity(MetricsKey.CPU_ID_ENGINE, cpuId);
    }

    public void onStart() {
        log.info("執行冷啟動索引重建與預熱...");
        workCount = 0;
        unflushedWorkCount = 0;
        MetricsCollector.set(MetricsKey.POLL_COUNT, 0L);
        MetricsCollector.set(MetricsKey.WORK_COUNT, 0L);
        MetricsCollector.set(MetricsKey.AERON_DROPPED_COUNT, 0L);

        ledger.rebuildAssetIndexes();
        OrderBook.rebuildActiveOrdersIndexes();
        orderProcessor.coldStartRebuild();
        OrderBook.get(1001); 

        WalProgress saved = metadata.get(MetaDataKey.Wal.MACHING_ENGINE_POINT);
        if (saved != null) progress.copyFrom(saved);
        
        log.info("Engine 啟動完成，恢復點: Seq={}", progress.getLastProcessedMsgSeq());
    }

    /** 
     每輪主循環觸發一次，處理落地與指標更新
     */
    public void tick(int done) {
        if (done > 0) {
            updateWorkMetrics(done);
            unflushedWorkCount += done;
        }

        final long now = open.vincentf13.service.spot.infra.util.Clock.now();

        // 智慧型落地策略：有未落地資料，且達到數量閾值或時間閾值 (解決閒置時尾部數據不落地的 bug)
        if (unflushedWorkCount > 0 && (unflushedWorkCount >= 50000 || now - lastFlushTime >= 100)) {
            flushAll();
            lastFlushTime = now;
            unflushedWorkCount = 0;
        }

        final long nowSec = now / 1000;
        if (nowSec > lastMetricsSec) {
            updateSystemMetrics(nowSec);
            lastMetricsSec = nowSec;
        }
    }

    private void updateWorkMetrics(int done) {
        workCount += done;
        localWorkCount += done;
        localPollCount++;

        if (localWorkCount >= METRICS_BATCH_SIZE) {
            MetricsCollector.add(MetricsKey.WORK_COUNT, localWorkCount);
            MetricsCollector.add(MetricsKey.POLL_COUNT, localPollCount);
            localWorkCount = 0;
            localPollCount = 0;
        }
    }

    private void flushAll() {
        ledger.flush();
        orderProcessor.flush();
        for (OrderBook book : OrderBook.getInstances()) {
            book.flush();
        }
        metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
    }

    @Override
    public void onMessage(int msgType, org.agrona.DirectBuffer buffer, int offset, int length) {
        final long gatewayTime = buffer.getLong(offset + 12);
        long seq = router.route(msgType, buffer, offset, length, gatewayTime, progress);
        if (seq != MSG_SEQ_NONE) {
            long last = progress.getLastProcessedMsgSeq();
            if (last != MSG_SEQ_NONE && seq != last + 1) {
                log.error("指令跳號！期望: {}, 實際: {}", last + 1, seq);
            }
            progress.setLastProcessedMsgSeq(seq);
        }
    }

    private void updateSystemMetrics(long nowSec) {
        Runtime r = Runtime.getRuntime();
        MetricsCollector.set(MetricsKey.MATCHING_JVM_USED_MB, (r.totalMemory() - r.freeMemory()) / 1024 / 1024);
        MetricsCollector.set(MetricsKey.MATCHING_JVM_MAX_MB, r.maxMemory() / 1024 / 1024);
        
        final long totalMatches = OrderBook.TOTAL_MATCH_COUNT.get();
        MetricsCollector.set(MetricsKey.MATCH_COUNT, totalMatches);
        Storage.self().metricsHistory().put(nowSec, totalMatches);

        if (java.lang.management.ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            MetricsCollector.set(MetricsKey.MATCHING_CPU_LOAD, (long)(os.getCpuLoad() * 100));
        }
    }

    public void onStop() { 
        metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
        reporter.close(); 
        ThreadContext.cleanup(); 
        log.info("Engine 已停止並存檔，最終位點: Seq={}", progress.getLastProcessedMsgSeq());
    }
}
