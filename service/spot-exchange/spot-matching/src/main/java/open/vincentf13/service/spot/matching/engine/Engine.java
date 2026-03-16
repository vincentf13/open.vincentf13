package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.WalProgress;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 撮合引擎執行緒 (Engine Orchestrator)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Engine extends Worker {
    private final ChronicleQueue engineReceiverWal = Storage.self().engineReceiverWal();
    private final ChronicleMap<Byte, WalProgress> metadata = Storage.self().walMetadata();

    private final CommandRouter router;
    private final OrderProcessor orderProcessor;
    private final Ledger ledger;
    private final ExecutionReporter reporter;
    private final SnapshotService snapshotService;
    
    private final WalProgress progress = new WalProgress();
    private ExcerptTailer tailer;
    private boolean isReplaying = false;
    private long lastSnapshotSeq = MSG_SEQ_NONE;

    private final net.openhft.chronicle.wire.ReadMarshallable walReader = this::onWalMessage;

    @PostConstruct public void init() { start("core-matching-engine"); }

    @Override
    protected void onStart() {
        final boolean recoveredFromSnapshot = snapshotService.recoverFromLatestSnapshot();
        if (!recoveredFromSnapshot) {
            log.info("未發現有效快照，執行全量數據校準與索引重建...");
            ledger.rebuildAssetIndexes();
            OrderBook.rebuildActiveOrdersIndexes();
            orderProcessor.coldStartRebuild();
        }
        
        // --- 壓測優化：強制初始化測試交易對 (SID=1001, BTC/USDT) ---
        log.info("[ENGINE-INIT] 初始化測試交易對: sid=1001 (BTC/USDT)");
        OrderBook.get(1001); 

        loadMetadata();
        this.tailer = engineReceiverWal.createTailer();
        alignTailer();
        log.info("Engine 啟動完成，當前模式: {}", isReplaying ? "REPLAYING" : "REAL-TIME");
    }

    private long lastMetricsTime = 0;
    private long localPollCount = 0;
    private long localWorkCount = 0;

    private static final int MAX_BATCH_SIZE = 1000;
    private static final int METRICS_BATCH_SIZE = 50000;

    @Override
    protected int doWork() {
        int workDone = 0;
        // 批次化讀取：在一個迴圈內盡量消耗當前已寫入 WAL 的數據，減少獲取 DocumentContext 的次數
        for (int i = 0; i < MAX_BATCH_SIZE; i++) {
            try (DocumentContext dc = tailer.readingDocument()) {
                if (!dc.isPresent()) {
                    break;
                }

                final long index = dc.index();
                final net.openhft.chronicle.wire.WireIn wire = dc.wire();
                final long gwSeq = router.routeRaw(wire, this::nextOrderId, this::nextTradeId);

                localWorkCount++;
                localPollCount++;
                workDone++;

                if (gwSeq != MSG_SEQ_NONE) {
                    updateMode(index, gwSeq);
                    checkSequence(gwSeq);
                    handlePersistence(index, gwSeq);
                }
            }
        }

        // 定時/定量更新指標
        long now = System.currentTimeMillis() / 1000;
        if (now > lastMetricsTime || localPollCount >= METRICS_BATCH_SIZE) {
            if (now > lastMetricsTime) {
                long totalMatches = OrderBook.TOTAL_MATCH_COUNT.get();
                Storage.self().metricsHistory().put(now, totalMatches);
                lastMetricsTime = now;
            }
            flushMetrics();
        }

        return workDone > 0 ? 1 : 0;
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

    private void onWalMessage(WireIn wire) {
        // 該方法已棄用，邏輯已移至 doWork 以支援 RAW 模式
    }

    private void loadMetadata() {
        WalProgress saved = metadata.get(MetaDataKey.Wal.MACHING_ENGINE_POINT);
        if (saved != null) {
            progress.copyFrom(saved);
            lastSnapshotSeq = progress.getLastProcessedMsgSeq();
            log.info("已加載元數據位點: Index={}, MsgSeq={}", progress.getLastProcessedIndex(), progress.getLastProcessedMsgSeq());
        } else {
            log.warn("元數據不存在，重置進度位點。");
            progress.reset();
        }
    }

    private void alignTailer() {
        if (progress.getLastProcessedIndex() != WAL_INDEX_NONE) {
            setReplaying(true); 
            tailer.moveToIndex(progress.getLastProcessedIndex());
            log.info("正在重播增量 WAL 以校準記憶體狀態...");
        } else {
            setReplaying(false);
            tailer.toStart();
        }
    }

    private void updateMode(long index, long gwSeq) {
        if (isReplaying && index >= tailer.queue().lastIndex()) {
            setReplaying(false);
            log.info("重播完成，切換至實時模式 (gwSeq: {})", gwSeq);
        }
    }

    private void checkSequence(long gwSeq) {
        final long lastSeq = progress.getLastProcessedMsgSeq();
        if (lastSeq != MSG_SEQ_NONE && gwSeq != MSG_SEQ_NONE && gwSeq != lastSeq + 1 && gwSeq != lastSeq) {
            log.error("指令跳號！期望: {}, 實際: {}。", lastSeq + 1, gwSeq);
        }
    }

    private void handlePersistence(long index, long gwSeq) {
        progress.setLastProcessedIndex(index);
        progress.setLastProcessedMsgSeq(gwSeq);
        if (index % 100 == 0) metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
        // 將快照頻率大幅調低，避免壓測時瘋狂寫磁碟導致 TPS 暴跌
        if (!isReplaying && gwSeq != MSG_SEQ_NONE && gwSeq - lastSnapshotSeq >= 100_000_000) {
            snapshotService.createSnapshot(progress);
            lastSnapshotSeq = gwSeq;
        }
    }

    private void setReplaying(boolean val) { this.isReplaying = val; reporter.setReplaying(val); }
    private long nextOrderId() { long id = progress.getOrderIdCounter(); progress.setOrderIdCounter(id + 1); return id; }
    private long nextTradeId() { long id = progress.getTradeIdCounter(); progress.setTradeIdCounter(id + 1); return id; }

    @Override protected void onStop() { reporter.close(); ThreadContext.cleanup(); }
}
