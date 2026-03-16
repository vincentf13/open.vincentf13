package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.MetricsCollector;
import open.vincentf13.service.spot.model.WalProgress;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 * 撮合引擎核心 (Matching Engine)
 * 職責：極簡化的指令分發與進度持久化中心。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Engine extends Worker {
    private final ChronicleMap<Byte, WalProgress> metadata = Storage.self().walMetadata();
    private final CommandRouter router;
    private final OrderProcessor orderProcessor;
    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    private final WalProgress progress = new WalProgress();
    private final org.agrona.concurrent.MessageHandler handler = this::onMessage;

    private long lastMetricsSec = 0;
    private long pollCount = 0, workCount = 0;
    private static final int BATCH_SIZE = Matching.ENGINE_BATCH_SIZE;

    @PostConstruct public void init() { start("core-matching-engine"); }

    @Override protected void onBind(int cpuId) {
        MetricsCollector.recordCpuAffinity(MetricsKey.CPU_ID_ENGINE, cpuId);
    }

    @Override protected void onStart() {
        log.info("執行冷啟動索引重建與預熱...");
        ledger.rebuildAssetIndexes();
        OrderBook.rebuildActiveOrdersIndexes();
        orderProcessor.coldStartRebuild();
        OrderBook.get(1001); 

        WalProgress saved = metadata.get(MetaDataKey.Wal.MACHING_ENGINE_POINT);
        if (saved != null) progress.copyFrom(saved);
        log.info("Engine 啟動完成，恢復點: Seq={}", progress.getLastProcessedMsgSeq());
    }

    @Override protected int doWork() {
        pollCount++;
        int done = Storage.self().engineWorkQueue().read(handler, BATCH_SIZE);
        workCount += done;

        long nowSec = System.currentTimeMillis() / 1000;
        if (nowSec > lastMetricsSec) {
            updateMetrics(nowSec);
            lastMetricsSec = nowSec;
        }
        return done > 0 ? 1 : 0;
    }

    private void onMessage(int msgType, org.agrona.DirectBuffer buffer, int offset, int length) {
        long seq = router.route(msgType, buffer, offset, length, progress::getAndIncrOrderId, progress::getAndIncrTradeId);
        if (seq != MSG_SEQ_NONE) {
            if (seq != progress.getLastProcessedMsgSeq() + 1 && progress.getLastProcessedMsgSeq() != MSG_SEQ_NONE) {
                log.error("指令跳號！期望: {}, 實際: {}", progress.getLastProcessedMsgSeq() + 1, seq);
            }
            progress.setLastProcessedMsgSeq(seq);
            if (seq % 100 == 0) metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
        }
    }

    private void updateMetrics(long nowSec) {
        Runtime r = Runtime.getRuntime();
        MetricsCollector.set(MetricsKey.MATCHING_JVM_USED_MB, (r.totalMemory() - r.freeMemory()) / 1024 / 1024);
        MetricsCollector.set(MetricsKey.MATCHING_JVM_MAX_MB, r.maxMemory() / 1024 / 1024);
        MetricsCollector.set(nowSec, OrderBook.TOTAL_MATCH_COUNT.get());
        
        MetricsCollector.set(MetricsKey.POLL_COUNT, pollCount);
        MetricsCollector.set(MetricsKey.WORK_COUNT, workCount);
        pollCount = workCount = 0;

        if (java.lang.management.ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            MetricsCollector.set(MetricsKey.MATCHING_CPU_LOAD, (long)(os.getCpuLoad() * 100));
        }
    }

    @Override protected void onStop() { reporter.close(); ThreadContext.cleanup(); }
}
