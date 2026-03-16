package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.WalProgress;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 * 撮合引擎執行緒 (Engine Orchestrator)
 * 職責：從 RingBuffer 讀取指令、執行領域模型處理、持久化進度與管理狀態恢復。
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
    private final org.agrona.concurrent.ringbuffer.MessageHandler ringBufferHandler = this::onRingBufferMessage;

    // 指標統計相關
    private long lastMetricsTime = 0;
    private long localPollCount = 0;
    private long localWorkCount = 0;
    private static final int MAX_BATCH_SIZE = 1000;
    private static final int METRICS_FLUSH_BATCH = 5000;

    @PostConstruct public void init() { start("core-matching-engine"); }

    @Override
    protected void onBind(int cpuId) {
        Storage.self().metricsHistory().put(Storage.KEY_CPU_ID_ENGINE, (long) cpuId);
    }

    @Override
    protected void onStart() {
        log.info("執行冷啟動索引重建...");
        ledger.rebuildAssetIndexes();
        OrderBook.rebuildActiveOrdersIndexes();
        orderProcessor.coldStartRebuild();
        
        // 預熱核心交易對
        OrderBook.get(1001); 

        loadMetadata();
        log.info("Engine 啟動完成，進入實時純內存模式 (REAL-TIME)");
    }

    @Override
    protected int doWork() {
        // 從 RingBuffer 輪詢待處理指令 (零拷貝)
        int workDone = Storage.self().engineWorkQueue().read(ringBufferHandler, MAX_BATCH_SIZE);
        
        localPollCount += MAX_BATCH_SIZE; 
        localWorkCount += workDone;

        // 定時/定量更新系統指標
        long now = System.currentTimeMillis() / 1000;
        if (now > lastMetricsTime || localPollCount >= METRICS_FLUSH_BATCH) {
            updateMetrics(now);
        }

        return workDone > 0 ? 1 : 0;
    }

    private void onRingBufferMessage(int msgType, org.agrona.DirectBuffer buffer, int offset, int length) {
        final long gwSeq = router.route(msgType, buffer, offset, length, this::nextOrderId, this::nextTradeId);
        if (gwSeq != MSG_SEQ_NONE) {
            checkSequence(gwSeq);
            persistProgress(gwSeq);
        }
    }

    private void updateMetrics(long now) {
        // 1. 更新 JVM 與 CPU 指標
        Runtime r = Runtime.getRuntime();
        Storage.self().metricsHistory().put(Storage.KEY_MATCHING_JVM_USED_MB, (r.totalMemory() - r.freeMemory()) / 1024 / 1024);
        
        if (java.lang.management.ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            Storage.self().metricsHistory().put(Storage.KEY_MATCHING_CPU_LOAD, (long)(sunOsBean.getCpuLoad() * 100));
        }

        // 2. 每秒更新一次總成交數
        if (now > lastMetricsTime) {
            Storage.self().metricsHistory().put(now, OrderBook.TOTAL_MATCH_COUNT.get());
            lastMetricsTime = now;
        }

        // 3. 累加處理計數器
        final long p = localPollCount;
        final long w = localWorkCount;
        Storage.self().metricsHistory().compute(Storage.KEY_POLL_COUNT, (k, v) -> v == null ? p : v + p);
        Storage.self().metricsHistory().compute(Storage.KEY_WORK_COUNT, (k, v) -> v == null ? w : v + w);
        localPollCount = 0;
        localWorkCount = 0;
    }

    private void loadMetadata() {
        WalProgress saved = metadata.get(MetaDataKey.Wal.MACHING_ENGINE_POINT);
        if (saved != null) {
            progress.copyFrom(saved);
            log.info("已加載元數據位點: MsgSeq={}", progress.getLastProcessedMsgSeq());
        } else {
            log.warn("元數據不存在，重置進度位點。");
            progress.reset();
        }
    }

    private void checkSequence(long gwSeq) {
        final long lastSeq = progress.getLastProcessedMsgSeq();
        if (lastSeq != MSG_SEQ_NONE && gwSeq != lastSeq + 1 && gwSeq != lastSeq) {
            log.error("指令跳號！期望: {}, 實際: {}。", lastSeq + 1, gwSeq);
        }
    }

    private void persistProgress(long gwSeq) {
        progress.setLastProcessedMsgSeq(gwSeq);
        // 每 100 筆指令持久化一次位點至元數據 Map
        if (gwSeq % 100 == 0) {
            metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
        }
    }

    private long nextOrderId() { long id = progress.getOrderIdCounter(); progress.setOrderIdCounter(id + 1); return id; }
    private long nextTradeId() { long id = progress.getTradeIdCounter(); progress.setTradeIdCounter(id + 1); return id; }

    @Override protected void onStop() { reporter.close(); ThreadContext.cleanup(); }
}
