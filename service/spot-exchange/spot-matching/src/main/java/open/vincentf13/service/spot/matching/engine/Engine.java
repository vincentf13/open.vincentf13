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
    private final open.vincentf13.service.spot.matching.aeron.AeronReceiver aeronReceiver;
    
    private final WalProgress progress = new WalProgress();
    private final open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver.AeronMessageHandler handler = this::onMessage;

    private long lastMetricsSec = 0;
    private long lastFlushTime = 0;
    private long pollCount = 0, workCount = 0;
    private long localPollCount = 0, localWorkCount = 0;
    private static final int METRICS_BATCH_SIZE = 5000;
    private static final int BATCH_SIZE = Matching.ENGINE_BATCH_SIZE;

    @PostConstruct public void init() { start("core-matching-engine"); }

    @Override protected void onBind(int cpuId) {
        MetricsCollector.recordCpuAffinity(MetricsKey.CPU_ID_ENGINE, cpuId);
    }

    @Override protected void onStart() {
        log.info("執行冷啟動索引重建與預熱...");
        // 1. 強制重置本地計數與 Metrics 磁碟存儲
        pollCount = 0;
        workCount = 0;
        MetricsCollector.set(MetricsKey.POLL_COUNT, 0L);
        MetricsCollector.set(MetricsKey.WORK_COUNT, 0L);
        MetricsCollector.set(MetricsKey.AERON_DROPPED_COUNT, 0L);

        ledger.rebuildAssetIndexes();
        OrderBook.rebuildActiveOrdersIndexes();
        orderProcessor.coldStartRebuild();
        OrderBook.get(1001); 

        WalProgress saved = metadata.get(MetaDataKey.Wal.MACHING_ENGINE_POINT);
        if (saved != null) progress.copyFrom(saved);
        
        // 初始化 Aeron 接收器 (被動模式)
        aeronReceiver.setup();
        
        log.info("Engine 啟動完成，恢復點: Seq={}", progress.getLastProcessedMsgSeq());
    }

    @Override protected int doWork() {
        final int done = aeronReceiver.poll(handler, BATCH_SIZE);
        
        if (done > 0) {
            workCount += done;
            localWorkCount += done;
            
            // 只有在處理真實數據時才增加 pollCount 觀測
            pollCount++;
            localPollCount++;

            if (localWorkCount >= METRICS_BATCH_SIZE) {
                MetricsCollector.add(MetricsKey.WORK_COUNT, localWorkCount);
                MetricsCollector.add(MetricsKey.POLL_COUNT, localPollCount);
                localWorkCount = 0;
                localPollCount = 0;
            }
        }

        // --- 性能優化：智慧型落地策略 ---
        final long now = open.vincentf13.service.spot.infra.util.Clock.now();
        if (done > 0 && (workCount % 50000 == 0 || now - lastFlushTime >= 100)) {
            ledger.flush();
            orderProcessor.flush();
            for (OrderBook book : OrderBook.getInstances()) {
                book.flush();
            }
            metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
            lastFlushTime = now;
        }

        final long nowSec = now / 1000;
        if (nowSec > lastMetricsSec) {
            updateMetrics(nowSec);
            lastMetricsSec = nowSec;
        }
        
        return done;
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
        }
    }

    private void updateMetrics(long nowSec) {
        Runtime r = Runtime.getRuntime();
        MetricsCollector.set(MetricsKey.MATCHING_JVM_USED_MB, (r.totalMemory() - r.freeMemory()) / 1024 / 1024);
        MetricsCollector.set(MetricsKey.MATCHING_JVM_MAX_MB, r.maxMemory() / 1024 / 1024);
        MetricsCollector.set(nowSec, OrderBook.TOTAL_MATCH_COUNT.get());

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
