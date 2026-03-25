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
    private long localWorkCount = 0;
    private long effectivePollCount = 0;
    private static final int METRICS_BATCH_SIZE = 5000;

    private final open.vincentf13.service.spot.infra.chronicle.LongValue metricKey = new open.vincentf13.service.spot.infra.chronicle.LongValue();
    private final open.vincentf13.service.spot.infra.chronicle.LongValue metricValue = new open.vincentf13.service.spot.infra.chronicle.LongValue();

    public void onBind(int cpuId) {
        MetricsCollector.recordCpuAffinity(MetricsKey.CPU_ID_ENGINE, cpuId);
    }

    public void onStart() {
        log.info("執行冷啟動索引重建與預熱...");
        workCount = 0;
        unflushedWorkCount = 0;
        effectivePollCount = 0;
        
        // 移除錯誤的 MetricsCollector.set，改為直接重置持久化記憶體中的值，避免觸發 GAUGE 覆寫
        Storage.self().metricsHistory().put(new open.vincentf13.service.spot.infra.chronicle.LongValue(MetricsKey.POLL_COUNT), new open.vincentf13.service.spot.infra.chronicle.LongValue(0L));
        Storage.self().metricsHistory().put(new open.vincentf13.service.spot.infra.chronicle.LongValue(MetricsKey.WORK_COUNT), new open.vincentf13.service.spot.infra.chronicle.LongValue(0L));
        Storage.self().metricsHistory().put(new open.vincentf13.service.spot.infra.chronicle.LongValue(MetricsKey.AERON_DROPPED_COUNT), new open.vincentf13.service.spot.infra.chronicle.LongValue(0L));

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

        // 智慧型落地策略：降低落地頻率至 1000ms (1秒) 以提升吞吐量
        // 過於頻繁的 flush (100ms) 會導致頻繁的磁碟 Sync，嚴重拖慢撮合速度
        if (unflushedWorkCount > 0 && (unflushedWorkCount >= 100000 || now - lastFlushTime >= 1000)) {
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
        effectivePollCount++; 

        if (localWorkCount >= METRICS_BATCH_SIZE) {
            MetricsCollector.add(MetricsKey.WORK_COUNT, localWorkCount);
            MetricsCollector.add(MetricsKey.POLL_COUNT, effectivePollCount);
            localWorkCount = 0;
            effectivePollCount = 0;
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
                log.error("❌ [FATAL] 指令跳號！期望: {}, 實際: {}。系統將立即停機以保護數據一致性。", last + 1, seq);
                System.exit(1); 
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
        
        // 修正：TPS 歷史紀錄應該儲存引擎總共處理的「交易數 (workCount)」，而非只有「成交數 (totalMatches)」   
        metricKey.set(nowSec);
        metricValue.set(workCount);
        Storage.self().metricsHistory().put(metricKey, metricValue);

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
