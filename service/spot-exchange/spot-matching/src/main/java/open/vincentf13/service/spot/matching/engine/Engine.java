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
 * 撮合引擎核心 (Matching Engine) - 智慧型落地優化版
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
    private long lastFlushTime = 0;
    
    // 性能優化：局部計數器，每 1000 筆才彙報非同步指標
    private long localPollCount = 0, localWorkCount = 0;
    private static final int METRICS_BATCH_SIZE = 1000;
    
    private static final int BATCH_SIZE = Matching.ENGINE_BATCH_SIZE;

    @PostConstruct public void init() { start("core-matching-engine"); }

    @Override protected void onBind(int cpuId) {
        MetricsCollector.recordCpuAffinity(MetricsKey.CPU_ID_ENGINE, cpuId);
    }

    @Override protected void onStart() {
        log.info("執行冷啟動索引重建與預熱...");
        // 1. 重置指標
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

    @Override protected int doWork() {
        int done = Storage.self().engineWorkQueue().read(handler, BATCH_SIZE);
        
        // 性能優化：每 1000 次才彙報非同步指標
        if (done > 0) {
            localWorkCount += done;
            if (localWorkCount >= METRICS_BATCH_SIZE) {
                MetricsCollector.add(MetricsKey.WORK_COUNT, localWorkCount);
                localWorkCount = 0;
            }
        }
        
        if (++localPollCount >= METRICS_BATCH_SIZE) {
            MetricsCollector.add(MetricsKey.POLL_COUNT, localPollCount);
            localPollCount = 0;
        }

        // --- 性能優化：智慧型落地策略 ---
        long now = open.vincentf13.service.spot.infra.util.Clock.now();
        if (now - lastFlushTime >= 20) {
            ledger.flush();
            orderProcessor.flush();
            for (OrderBook book : OrderBook.getInstances()) {
                book.flush();
            }
            lastFlushTime = now;
        }

        long nowSec = now / 1000;
        if (nowSec > lastMetricsSec) {
            updateMetrics(nowSec);
            lastMetricsSec = nowSec;
        }
        
        if (done == 0) {
            Thread.onSpinWait(); 
            return 0;
        }
        return 1;
    }

    private void onMessage(int msgType, org.agrona.DirectBuffer buffer, int offset, int length) {
        // 按照 AbstractSbeModel 定義，Timestamp 在第 12 個位元組之後
        final long gatewayTime = buffer.getLong(offset + 12);
        long seq = router.route(msgType, buffer, offset, length, gatewayTime, progress);
        if (seq != MSG_SEQ_NONE) {
            if (seq != progress.getLastProcessedMsgSeq() + 1 && progress.getLastProcessedMsgSeq() != MSG_SEQ_NONE) {
                log.error("指令跳號！期望: {}, 實際: {}", progress.getLastProcessedMsgSeq() + 1, seq);
            }
            progress.setLastProcessedMsgSeq(seq);
            if (seq % 1000 == 0) metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
        }
    }

    private void updateMetrics(long nowSec) {
        Runtime r = Runtime.getRuntime();
        MetricsCollector.set(MetricsKey.MATCHING_JVM_USED_MB, (r.totalMemory() - r.freeMemory()) / 1024 / 1024);
        MetricsCollector.set(MetricsKey.MATCHING_JVM_MAX_MB, r.maxMemory() / 1024 / 1024);
        
        // 性能優化：每秒強制推送剩餘的計數，確保監控介面不會因為未滿 1000 而顯示 0
        if (localPollCount > 0) {
            MetricsCollector.add(MetricsKey.POLL_COUNT, localPollCount);
            localPollCount = 0;
        }
        if (localWorkCount > 0) {
            MetricsCollector.add(MetricsKey.WORK_COUNT, localWorkCount);
            localWorkCount = 0;
        }

        if (java.lang.management.ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            MetricsCollector.set(MetricsKey.MATCHING_CPU_LOAD, (long)(os.getCpuLoad() * 100));
        }
    }

    @Override protected void onStop() { 
        metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress); // 確保關閉時存檔
        reporter.close(); 
        ThreadContext.cleanup(); 
    }
}
