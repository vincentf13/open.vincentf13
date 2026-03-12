package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.*;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 撮合引擎執行緒 (Engine Orchestrator)
 職責：指令路由、重播管理與全局連續性一致性校驗
 */
@Slf4j
@Component
public class Engine extends Worker {
    private final OrderProcessor orderProcessor;
    private final SystemProcessor systemProcessor;
    private final ExecutionReporter reporter;
    
    private final Progress progress = new Progress();
    private ExcerptTailer tailer;
    private boolean isReplaying = false;

    // 預分配讀取緩衝區
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(512);

    public Engine(OrderProcessor orderProcessor, SystemProcessor systemProcessor, ExecutionReporter reporter) {
        this.orderProcessor = orderProcessor;
        this.systemProcessor = systemProcessor;
        this.reporter = reporter;
    }

    @PostConstruct public void init() { start("core-matching-engine"); }

    @Override
    protected void onStart() {
        this.tailer = Storage.self().commandQueue().createTailer();
        
        Progress saved = Storage.self().metadata().get(MetaDataKey.MACHING_ENGINE_POINT);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            progress.setLastProcessedGwSeq(saved.getLastProcessedGwSeq());
            progress.setOrderIdCounter(saved.getOrderIdCounter());
            progress.setTradeIdCounter(saved.getTradeIdCounter());
        } else {
            progress.setOrderIdCounter(1); progress.setTradeIdCounter(1);
        }

        log.info("正在恢復內存狀態 (最後 GW 序號: {})...", progress.getLastProcessedGwSeq());
        Storage.self().activeOrders().keySet().forEach(id -> {
            Order o = Storage.self().orders().get(id);
            if (o != null && o.getStatus() < 2) orderProcessor.rebuildIndex(o);
            else Storage.self().activeOrders().remove(id);
        });

        if (progress.getLastProcessedSeq() > 0) {
            isReplaying = true; 
            tailer.moveToIndex(progress.getLastProcessedSeq());
            log.info("啟動重播恢復模式，位點: {}", progress.getLastProcessedSeq());
        }
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            int msgType = wire.read(ChronicleWireKey.msgType).int32();
            long gwSeq = wire.read(ChronicleWireKey.gwSeq).int64();
            
            if (isReplaying && seq >= tailer.queue().lastIndex()) {
                isReplaying = false;
                log.info("狀態恢復完成，進入實時模式 (gwSeq: {})", gwSeq);
            }

            // --- 全局連續性與冪等性斷言 (Sequence Continuity Assertion) ---
            long lastSeq = progress.getLastProcessedGwSeq();
            if (lastSeq != -1) {
                if (gwSeq == lastSeq) {
                    log.warn("檢測到重複指令 (gwSeq: {})，已自動過濾", gwSeq);
                    return;
                }
                if (gwSeq != lastSeq + 1) {
                    // 發生嚴重一致性錯誤：指令流不連續，這將導致狀態機損壞
                    log.error("致命錯誤：指令序號跳號！期望: {}, 實際: {}。為保護帳務一致性，系統將立即停機。", lastSeq + 1, gwSeq);
                    System.exit(1); 
                }
            }

            // 零拷貝提取數據地址
            reusableBytes.clear(); 
            wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
            payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());

            // --- 業務分發 ---
            if (msgType == MsgType.AUTH) {
                systemProcessor.handleAuth(wire.read(ChronicleWireKey.userId).int64(), gwSeq, isReplaying);
            } else if (msgType == MsgType.ORDER_CREATE) {
                orderProcessor.processCreateCommand(payloadBuffer, gwSeq, isReplaying, 
                    this::nextOrderId, this::nextTradeId);
            }

            // --- 狀態落盤與一致性確認 ---
            progress.setLastProcessedSeq(seq);
            progress.setLastProcessedGwSeq(gwSeq);
            Storage.self().metadata().put(MetaDataKey.MACHING_ENGINE_POINT, progress);
        });
        
        if (!handled && isReplaying) isReplaying = false;
        return handled ? 1 : 0;
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
        reusableBytes.releaseLast(); 
        reporter.close();
    }
}
