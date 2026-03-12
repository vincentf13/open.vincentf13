package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.*;
import open.vincentf13.service.spot.sbe.*;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 撮合引擎執行緒 (Engine Orchestrator)
 職責：作為系統的編排者，負責指令讀取、狀態管理與邏輯分發
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
            progress.setOrderIdCounter(saved.getOrderIdCounter());
            progress.setTradeIdCounter(saved.getTradeIdCounter());
        } else {
            progress.setOrderIdCounter(1); progress.setTradeIdCounter(1);
        }

        log.info("正在恢復內存狀態...");
        Storage.self().activeOrders().keySet().forEach(id -> {
            Order o = Storage.self().orders().get(id);
            if (o != null && o.getStatus() < 2) orderProcessor.rebuildIndex(o);
            else Storage.self().activeOrders().remove(id);
        });

        if (progress.getLastProcessedSeq() > 0) {
            isReplaying = true; 
            tailer.moveToIndex(progress.getLastProcessedSeq());
            log.info("引擎進入重播模式，起始位點: {}", progress.getLastProcessedSeq());
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
                log.info("重播結束，切換至實時模式");
            }

            // 提取二進位 Payload 位址
            reusableBytes.clear(); 
            wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
            payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());

            // --- 指令分發 (Router) ---
            if (msgType == MsgType.AUTH) {
                // AUTH 指令直接提取 userId
                systemProcessor.handleAuth(wire.read(ChronicleWireKey.userId).int64(), gwSeq, isReplaying);
            } else if (msgType == MsgType.ORDER_CREATE) {
                dispatchOrderCreate(gwSeq);
            }

            // 更新與保存進度
            progress.setLastProcessedSeq(seq);
            Storage.self().metadata().put(MetaDataKey.MACHING_ENGINE_POINT, progress);
        });
        
        if (!handled && isReplaying) isReplaying = false;
        return handled ? 1 : 0;
    }

    /** 訂單指令分發與冪等校驗 */
    private void dispatchOrderCreate(long gwSeq) {
        SbeCodec.decode(payloadBuffer, 0, createDecoder);
        String cid = createDecoder.clientOrderId();
        CidKey key = new CidKey(createDecoder.userId(), cid);
        
        Long resId = Storage.self().cids().get(key);
        if (resId != null) {
            if (!isReplaying) {
                Order o = Storage.self().orders().get(resId);
                if (o != null) reporter.resendReport(o, gwSeq);
            }
            return;
        }
        
        long orderId = progress.getOrderIdCounter(); 
        progress.setOrderIdCounter(orderId + 1);
        
        // 委派交易業務，傳遞確定性 tradeId 生成器
        orderProcessor.handleOrderCreate(createDecoder, gwSeq, orderId, cid, isReplaying, () -> {
            long tid = progress.getTradeIdCounter();
            progress.setTradeIdCounter(tid + 1);
            return tid;
        });
        
        Storage.self().cids().put(key, orderId);
    }

    @Override protected void onStop() { 
        reusableBytes.releaseLast(); 
        reporter.close();
    }
}
