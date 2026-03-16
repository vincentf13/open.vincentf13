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
 撮合引擎執行緒 (Engine Orchestrator)
 採用 Agrona RingBuffer 實現 Aeron 接收端到引擎核心的純內存零拷貝通訊。
 徹底移除本地 WAL 依賴與重播邏輯，最大化實時撮合性能。
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
    private final SnapshotService snapshotService;
    
    private final WalProgress progress = new WalProgress();
    private long lastSnapshotSeq = MSG_SEQ_NONE;

    private final org.agrona.concurrent.ringbuffer.MessageHandler ringBufferHandler = this::onRingBufferMessage;

    @PostConstruct public void init() { start("core-matching-engine"); }

    @Override
    protected void onBind(int cpuId) {
        Storage.self().metricsHistory().put(Storage.KEY_CPU_ID_ENGINE, (long) cpuId);
    }

    @Override
    protected void onStart() {
        // 從快照恢復最新記憶體狀態 (唯一持久化來源)
        final boolean recoveredFromSnapshot = snapshotService.recoverFromLatestSnapshot();
        if (!recoveredFromSnapshot) {
            log.info("未發現有效快照，執行冷啟動索引重建...");
            ledger.rebuildAssetIndexes();
            OrderBook.rebuildActiveOrdersIndexes();
            orderProcessor.coldStartRebuild();
        }
        
        // --- 壓測優化：強制初始化測試交易對 (SID=1001, BTC/USDT) ---
        log.info("[ENGINE-INIT] 初始化測試交易對: sid=1001 (BTC/USDT)");
        OrderBook.get(1001); 

        loadMetadata();
        log.info("Engine 啟動完成，進入實時純內存模式 (REAL-TIME)");
    }

    private long lastMetricsTime = 0;
    private long localPollCount = 0;
    private long localWorkCount = 0;

    private static final int MAX_BATCH_SIZE = 1000;
    private static final int METRICS_BATCH_SIZE = 5000;

    @Override
    protected int doWork() {
        // 從 Agrona RingBuffer 輪詢待處理指令 (零拷貝)
        int workDone = Storage.self().engineWorkQueue().read(ringBufferHandler, MAX_BATCH_SIZE);
        
        localPollCount += MAX_BATCH_SIZE; 
        localWorkCount += workDone;

        // 定時/定量更新指標
        long now = System.currentTimeMillis() / 1000;
        if (now > lastMetricsTime || localPollCount >= METRICS_BATCH_SIZE) {
            updateProcessMetrics();
            if (now > lastMetricsTime) {
                long totalMatches = OrderBook.TOTAL_MATCH_COUNT.get();
                Storage.self().metricsHistory().put(now, totalMatches);
                lastMetricsTime = now;
            }
            flushMetrics();
        }

        return workDone > 0 ? 1 : 0;
    }

    private void onRingBufferMessage(int msgType, org.agrona.DirectBuffer buffer, int offset, int length) {
        // 直接路由原始 DirectBuffer，避免 Chronicle Wire 的包裝開銷
        final long gwSeq = router.route(msgType, buffer, offset, length, this::nextOrderId, this::nextTradeId);
        if (gwSeq != MSG_SEQ_NONE) {
            checkSequence(gwSeq);
            // 更新進度與 Sequence 位點
            handlePersistence(gwSeq);
        }
    }

    private void updateProcessMetrics() {
        Runtime r = Runtime.getRuntime();
        Storage.self().metricsHistory().put(Storage.KEY_MATCHING_JVM_USED_MB, (r.totalMemory() - r.freeMemory()) / 1024 / 1024);
        Storage.self().metricsHistory().put(Storage.KEY_MATCHING_JVM_MAX_MB, r.maxMemory() / 1024 / 1024);
        
        java.lang.management.OperatingSystemMXBean osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            Storage.self().metricsHistory().put(Storage.KEY_MATCHING_CPU_LOAD, (long)(sunOsBean.getCpuLoad() * 100));
        }
    }

    private void flushMetrics() {
        if (localPollCount > 0) {
            final long p = localPollCount;
            final long w = localWorkCount;
            Storage.self().metricsHistory().compute(Storage.KEY_POLL_COUNT, (k, v) -> v == null ? p : v + p);
            Storage.self().metricsHistory().compute(Storage.KEY_WORK_COUNT, (k, v) -> v == null ? w : v + w);
            localPollCount = 0;
            localWorkCount = 0;
        }
    }

    private void loadMetadata() {
        WalProgress saved = metadata.get(MetaDataKey.Wal.MACHING_ENGINE_POINT);
        if (saved != null) {
            progress.copyFrom(saved);
            lastSnapshotSeq = progress.getLastProcessedMsgSeq();
            log.info("已加載元數據位點: MsgSeq={}", progress.getLastProcessedMsgSeq());
        } else {
            log.warn("元數據不存在，重置進度位點。");
            progress.reset();
        }
    }

    private void checkSequence(long gwSeq) {
        final long lastSeq = progress.getLastProcessedMsgSeq();
        if (lastSeq != MSG_SEQ_NONE && gwSeq != MSG_SEQ_NONE && gwSeq != lastSeq + 1 && gwSeq != lastSeq) {
            log.error("指令跳號！期望: {}, 實際: {}。", lastSeq + 1, gwSeq);
        }
    }

    private void handlePersistence(long gwSeq) {
        progress.setLastProcessedMsgSeq(gwSeq);
        // 定期將進度與 ID 計數器持久化到元數據 Map
        if (gwSeq % 100 == 0) metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
        
        // 依賴 Sequence 觸發快照
        if (gwSeq != MSG_SEQ_NONE && gwSeq - lastSnapshotSeq >= 100_000_000) {
            snapshotService.executeCreateSnapshot(progress);
            lastSnapshotSeq = gwSeq;
        }
    }

    private long nextOrderId() { long id = progress.getOrderIdCounter(); progress.setOrderIdCounter(id + 1); return id; }
    private long nextTradeId() { long id = progress.getTradeIdCounter(); progress.setTradeIdCounter(id + 1); return id; }

    @Override protected void onStop() { reporter.close(); ThreadContext.cleanup(); }
}
