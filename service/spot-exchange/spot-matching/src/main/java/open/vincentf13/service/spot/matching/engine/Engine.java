package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
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
        loadMetadata();
        this.tailer = engineReceiverWal.createTailer();
        alignTailer();
        log.info("Engine 啟動完成，當前模式: {}", isReplaying ? "REPLAYING" : "REAL-TIME");
    }

    @Override
    protected int doWork() {
        boolean success = tailer.readDocument(walReader);
        
        // 業務飽和度統計 (真正反映撮合引擎是否在忙碌處理業務)
        Storage.self().metricsHistory().compute(Storage.KEY_POLL_COUNT, (k, v) -> v == null ? 1L : v + 1);
        if (success) {
            Storage.self().metricsHistory().compute(Storage.KEY_WORK_COUNT, (k, v) -> v == null ? 1L : v + 1);
        }

        return success ? 1 : 0;
    }

    private void onWalMessage(WireIn wire) {
        final long index = tailer.index();
        
        // 1. 指令路由 (核心業務執行) - 改為 Raw 模式
        final long gwSeq = router.routeRaw(wire, this::nextOrderId, this::nextTradeId);

        // 2. 引擎狀態演進與連續性檢查
        updateMode(index, gwSeq);
        checkSequence(gwSeq);

        // 3. 進度持久化與快照觸發
        handlePersistence(index, gwSeq);
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
        if (!isReplaying && gwSeq != MSG_SEQ_NONE && gwSeq - lastSnapshotSeq >= 100_000) {
            snapshotService.createSnapshot(progress);
            lastSnapshotSeq = gwSeq;
        }
    }

    private void setReplaying(boolean val) { this.isReplaying = val; reporter.setReplaying(val); }
    private long nextOrderId() { long id = progress.getOrderIdCounter(); progress.setOrderIdCounter(id + 1); return id; }
    private long nextTradeId() { long id = progress.getTradeIdCounter(); progress.setTradeIdCounter(id + 1); return id; }

    @Override protected void onStop() { reporter.close(); ThreadContext.cleanup(); }
}
