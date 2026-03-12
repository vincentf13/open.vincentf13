package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
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
 職責：作為系統的編排者與路由器，負責指令讀取、狀態恢復切換與全局序號管理
 */
@Slf4j
@Component
public class Engine extends Worker {
    // 依賴的具體存儲結構 (僅限於路由與進度管理)
    private final ChronicleQueue gwToMatchingWal = Storage.self().gwToMatchingWal();
    private final ChronicleMap<Byte, Progress> metadata = Storage.self().metadata();

    private final OrderProcessor orderProcessor;
    private final SystemProcessor systemProcessor;
    
    private final Progress progress = new Progress();
    private ExcerptTailer tailer;
    private boolean isReplaying = false;

    // 預分配讀取緩衝區
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(512);

    public Engine(OrderProcessor orderProcessor, SystemProcessor systemProcessor) {
        this.orderProcessor = orderProcessor;
        this.systemProcessor = systemProcessor;
    }

    @PostConstruct public void init() { start("core-matching-engine"); }

    @Override
    protected void onStart() {
        this.tailer = gwToMatchingWal.createTailer();
        
        // 1. 加載全局進度
        Progress saved = metadata.get(MetaDataKey.MACHING_ENGINE_POINT);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            progress.setLastProcessedGwSeq(saved.getLastProcessedGwSeq());
            progress.setOrderIdCounter(saved.getOrderIdCounter());
            progress.setTradeIdCounter(saved.getTradeIdCounter());
        } else {
            progress.setOrderIdCounter(1); progress.setTradeIdCounter(1);
        }

        // 2. 驅動 Processor 恢復內部索引 (下沉邏輯執行)
        orderProcessor.rebuildState();

        // 3. 判斷重播範圍
        if (progress.getLastProcessedSeq() > 0) {
            isReplaying = true; 
            tailer.moveToIndex(progress.getLastProcessedSeq());
            log.info("啟動重播恢復模式，起始位點: {}", progress.getLastProcessedSeq());
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
                log.info("狀態重播結束，進入實時模式 (gwSeq: {})", gwSeq);
            }

            // --- 全局連續性斷言 (線性一致性防線) ---
            long lastSeq = progress.getLastProcessedGwSeq();
            if (lastSeq != -1) {
                if (gwSeq == lastSeq) return; 
                if (gwSeq != lastSeq + 1) {
                    log.error("致命錯誤：指令序號跳號！期望: {}, 實際: {}。", lastSeq + 1, gwSeq);
                    System.exit(1); 
                }
            }

            // 零拷貝提取 Payload
            reusableBytes.clear(); 
            wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
            payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());

            // --- 業務路由 ---
            if (msgType == MsgType.AUTH) {
                systemProcessor.handleAuth(wire.read(ChronicleWireKey.userId).int64(), gwSeq, isReplaying);
            } else if (msgType == MsgType.ORDER_CREATE) {
                // 完全下沉：由 Processor 負責解碼與冪等檢查
                orderProcessor.processCreateCommand(payloadBuffer, gwSeq, isReplaying, 
                    this::nextOrderId, this::nextTradeId);
            }

            // --- 狀態更新與保存 ---
            progress.setLastProcessedSeq(seq);
            progress.setLastProcessedGwSeq(gwSeq);
            metadata.put(MetaDataKey.MACHING_ENGINE_POINT, progress);
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
    }
}
