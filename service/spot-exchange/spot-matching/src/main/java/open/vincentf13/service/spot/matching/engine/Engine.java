package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.ReadMarshallable;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.alloc.NativeUnsafeBuffer;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.model.Progress.WalProgress;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 撮合引擎執行緒 (Engine Orchestrator)
 職責：指令路由、狀態管理與一致性檢查，實現熱點路徑零物件分配
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Engine extends Worker implements ReadMarshallable {
    private final ChronicleQueue gwToMatchingWal = Storage.self().gwToMatchingWal();
    private final ChronicleMap<Byte, WalProgress> metadata = Storage.self().walMetadata();

    private final OrderProcessor orderProcessor;
    private final AuthProcessor authProcessor;
    private final DepositProcessor depositProcessor;
    private final ExecutionReporter reporter;
    private final SnapshotService snapshotService;
    
    private final WalProgress progress = new WalProgress();
    private ExcerptTailer tailer;
    private boolean isReplaying = false;
    private long lastSnapshotSeq = MSG_SEQ_NONE;

    @PostConstruct public void init() { start("core-matching-engine"); }

    private void setReplaying(boolean val) {
        this.isReplaying = val;
        reporter.setReplaying(val);
    }

    @Override
    protected void onStart() {
        boolean recoveredFromSnapshot = false;
        // --- 核心改進：優先從快照恢復內存與磁碟狀態 ---
        if (snapshotService.recoverFromLatestSnapshot()) {
            log.info("從磁碟快照成功恢復所有 Chronicle Map 與 OrderBook 內存狀態！");
            recoveredFromSnapshot = true;
        }

        this.tailer = gwToMatchingWal.createTailer();
        
        WalProgress saved = metadata.get(MetaDataKey.Wal.MACHING_ENGINE_POINT);
        if (saved != null) {
            progress.setLastProcessedIndex(saved.getLastProcessedIndex());
            progress.setLastProcessedMsgSeq(saved.getLastProcessedMsgSeq());
            progress.setOrderIdCounter(saved.getOrderIdCounter());
            progress.setTradeIdCounter(saved.getTradeIdCounter());
            lastSnapshotSeq = progress.getLastProcessedMsgSeq();
            log.info("從元數據恢復位點: Index={}, MsgSeq={}, OrderId={}, TradeId={}", 
                progress.getLastProcessedIndex(), progress.getLastProcessedMsgSeq(),
                progress.getOrderIdCounter(), progress.getTradeIdCounter());
        } else {
            log.warn("元數據不存在，執行冷啟動...");
            progress.setOrderIdCounter(1); progress.setTradeIdCounter(1);
            progress.setLastProcessedIndex(WAL_INDEX_NONE);
            progress.setLastProcessedMsgSeq(MSG_SEQ_NONE);
        }

        // 如果不是從快照恢復的，才需要掃描 ChronicleMap 重建
        if (!recoveredFromSnapshot) {
            orderProcessor.coldStartRebuild();
        }

        // 自愈機制
        if (progress.getLastProcessedIndex() != WAL_INDEX_NONE) {
            setReplaying(true); 
            tailer.moveToIndex(progress.getLastProcessedIndex());
            log.info("✅ 引擎狀態自愈啟動：正在重播增量 WAL 以校準記憶體狀態...");
        } else {
            setReplaying(false);
            tailer.toStart();
        }
    }

    @Override
    protected int doWork() {
        // 優化：直接傳遞 this，消除 readDocument 的 Lambda 分配
        return tailer.readDocument(this) ? 1 : 0;
    }

    /** 
      指令處理回調：實現熱點路徑 Zero-Allocation 
     */
    @Override
    public void readMarshallable(WireIn wire) {
        final long index = tailer.index();
        final int msgType = wire.read(ChronicleWireKey.msgType).int32();
        final long gwSeq = wire.read(ChronicleWireKey.gwSeq).int64();
        
        // 狀態切換
        if (isReplaying && index >= tailer.queue().lastIndex()) {
            setReplaying(false);
            log.info("重播完成，切換至實時模式 (gwSeq: {})", gwSeq);
        }

        // 連續性斷言
        final long lastSeq = progress.getLastProcessedMsgSeq();
        if (lastSeq != MSG_SEQ_NONE) {
            if (gwSeq == lastSeq) return; 
            if (gwSeq != lastSeq + 1) {
                log.error("指令跳號！期望: {}, 實際: {}。請檢查 WAL 連續性或使用 ENGINE_FORCE_START=true 強制啟動。", lastSeq + 1, gwSeq);
                if (!"true".equalsIgnoreCase(System.getProperty("ENGINE_FORCE_START"))) {
                    System.exit(1); 
                }
            }
        }

        // 路由分發 (補全 DEPOSIT 與 ORDER_CANCEL)
        switch (msgType) {
            case MsgType.AUTH -> authProcessor.handleAuth(wire.read(ChronicleWireKey.userId).int64(), gwSeq);
            case MsgType.ORDER_CREATE -> {
                NativeUnsafeBuffer scratchBuffer = ThreadContext.get().getScratchBuffer();
                scratchBuffer.clear(); 
                wire.read(ChronicleWireKey.payload).bytes(scratchBuffer.bytes());
                orderProcessor.processCreateCommand(scratchBuffer.wrapForRead(), gwSeq, this::nextOrderId, this::nextTradeId);
            }
            case MsgType.ORDER_CANCEL -> {
                long uid = wire.read(ChronicleWireKey.userId).int64();
                long oid = wire.read(ChronicleWireKey.data).int64(); // 借用 data 傳遞 orderId
                orderProcessor.processCancelCommand(uid, oid, gwSeq);
            }
            case MsgType.DEPOSIT -> {
                long uid = wire.read(ChronicleWireKey.userId).int64();
                int assetId = wire.read(ChronicleWireKey.assetId).int32(); 
                long amount = wire.read(ChronicleWireKey.data).int64(); 
                depositProcessor.handleDeposit(uid, assetId, amount, gwSeq);
            }
            case MsgType.SNAPSHOT -> {
                if (!isReplaying) {
                    snapshotService.createSnapshot(progress);
                    lastSnapshotSeq = gwSeq;
                }
            }
        }

        // 進度存檔
        progress.setLastProcessedIndex(index);
        progress.setLastProcessedMsgSeq(gwSeq);

        // --- 核心改進：自動快照策略 ---
        // 每 100 條存檔元數據位點 (Checkpoint)
        if (index % 100 == 0) {
            metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
        }

        // 每 100,000 條執行完整磁碟快照 (Snapshot)
        if (!isReplaying && gwSeq - lastSnapshotSeq >= 100_000) {
            snapshotService.createSnapshot(progress);
            lastSnapshotSeq = gwSeq;
        }
    }

    private long nextOrderId() {
        long id = progress.getOrderIdCounter();
        progress.setOrderIdCounter(id + 1);
        return id;
    }

    private long nextTradeId() {
        long id = progress.getTradeIdCounter();
        progress.setTradeIdCounter(id + 1);
        return id;
    }

    @Override protected void onStop() { 
        reporter.close();
        ThreadContext.cleanup();
    }
}
