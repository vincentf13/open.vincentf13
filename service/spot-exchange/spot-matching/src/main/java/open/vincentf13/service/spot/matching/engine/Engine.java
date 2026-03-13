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
import open.vincentf13.service.spot.model.WalProgress;
import open.vincentf13.service.spot.model.command.*;
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
        ThreadContext ctx = ThreadContext.get();
        
        long gwSeq = MSG_SEQ_NONE;

        // 1. 根據類型讀取結構化數據
        switch (msgType) {
            case MsgType.AUTH -> {
                AuthCommand cmd = ctx.getAuthCommand();
                wire.read(ChronicleWireKey.payload).marshallable(cmd);
                gwSeq = cmd.getSeq();
                authProcessor.handleAuth(cmd.getUserId(), gwSeq);
            }
            case MsgType.ORDER_CREATE -> {
                OrderCreateCommand cmd = ctx.getOrderCreateCommand();
                wire.read(ChronicleWireKey.payload).marshallable(cmd);
                gwSeq = cmd.getSeq();
                NativeUnsafeBuffer scratchBuffer = ctx.getScratchBuffer();
                scratchBuffer.clear(); 
                wire.read(ChronicleWireKey.data).bytes(scratchBuffer.bytes());
                orderProcessor.processCreateCommand(scratchBuffer.wrapForRead(), gwSeq, this::nextOrderId, this::nextTradeId);
            }
            case MsgType.ORDER_CANCEL -> {
                OrderCancelCommand cmd = ctx.getOrderCancelCommand();
                wire.read(ChronicleWireKey.payload).marshallable(cmd);
                gwSeq = cmd.getSeq();
                orderProcessor.processCancelCommand(cmd.getUserId(), cmd.getOrderId(), gwSeq);
            }
            case MsgType.DEPOSIT -> {
                DepositCommand cmd = ctx.getDepositCommand();
                wire.read(ChronicleWireKey.payload).marshallable(cmd);
                gwSeq = cmd.getSeq();
                depositProcessor.handleDeposit(cmd.getUserId(), cmd.getAssetId(), cmd.getAmount(), gwSeq);
            }
            case MsgType.SNAPSHOT -> {
                SnapshotCommand cmd = ctx.getSnapshotCommand();
                wire.read(ChronicleWireKey.payload).marshallable(cmd);
                gwSeq = cmd.getSeq();
                if (!isReplaying) {
                    snapshotService.createSnapshot(progress);
                    lastSnapshotSeq = gwSeq;
                }
            }
        }

        // 2. 狀態與冪等檢查 (使用 gwSeq)
        if (isReplaying && index >= tailer.queue().lastIndex()) {
            setReplaying(false);
            log.info("重播完成，切換至實時模式 (gwSeq: {})", gwSeq);
        }

        final long lastSeq = progress.getLastProcessedMsgSeq();
        if (lastSeq != MSG_SEQ_NONE && gwSeq != MSG_SEQ_NONE) {
            if (gwSeq == lastSeq) return; 
            if (gwSeq != lastSeq + 1) {
                log.error("指令跳號！期望: {}, 實際: {}。請檢查 WAL 連續性。", lastSeq + 1, gwSeq);
            }
        }

        // 3. 進度存檔
        progress.setLastProcessedIndex(index);
        progress.setLastProcessedMsgSeq(gwSeq);

        if (index % 100 == 0) {
            metadata.put(MetaDataKey.Wal.MACHING_ENGINE_POINT, progress);
        }

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
