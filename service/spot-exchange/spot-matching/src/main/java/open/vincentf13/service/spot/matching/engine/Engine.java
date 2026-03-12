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
 職責：驅動指令隊列讀取、執行冪等性分發、管理重播狀態並協調 Processor 執行
 */
@Slf4j
@Component
public class Engine extends Worker {
    private final OrderProcessor processor;
    private final ExecutionReporter reporter;
    private final Ledger ledger;
    
    private final Progress progress = new Progress();
    private ExcerptTailer tailer;
    private boolean isReplaying = false;

    // 預分配 SBE 解碼組件
    private final OrderCreateDecoder createDecoder = new OrderCreateDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(512);

    public Engine(OrderProcessor processor, ExecutionReporter reporter, Ledger ledger) {
        this.processor = processor;
        this.reporter = reporter;
        this.ledger = ledger;
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

        log.info("正在恢復內存訂單簿狀態...");
        Storage.self().activeOrders().keySet().forEach(id -> {
            Order o = Storage.self().orders().get(id);
            if (o != null && o.getStatus() < 2) processor.rebuildIndex(o);
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
            
            if (isReplaying && seq >= tailer.queue().lastIndex()) isReplaying = false;

            reusableBytes.clear(); 
            wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
            payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());

            // 1. 指令分發
            if (msgType == MsgType.AUTH) handleAuth(gwSeq);
            else if (msgType == MsgType.ORDER_CREATE) dispatchOrderCreate(gwSeq);

            // 2. 更新與持久化進度
            progress.setLastProcessedSeq(seq);
            Storage.self().metadata().put(MetaDataKey.MACHING_ENGINE_POINT, progress);
        });
        
        if (!handled && isReplaying) isReplaying = false;
        return handled ? 1 : 0;
    }

    private void dispatchOrderCreate(long gwSeq) {
        SbeCodec.decode(payloadBuffer, 0, createDecoder);
        String cid = createDecoder.clientOrderId();
        CidKey key = new CidKey(createDecoder.userId(), cid);
        
        // 冪等性檢查
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
        
        // 委派至 Processor 執行業務
        Storage.self().cids().put(key, processor.handleOrderCreate(createDecoder, gwSeq, orderId, cid, isReplaying));
    }

    private void handleAuth(long gwSeq) {
        long userId = createDecoder.userId(); // 此處應從 Payload 或 Wire 讀取，目前簡化處理
        ledger.initBalance(userId, Asset.BTC, gwSeq); 
        ledger.initBalance(userId, Asset.USDT, gwSeq);
        reporter.sendAuthSuccess(userId, gwSeq, isReplaying);
    }

    @Override protected void onStop() { 
        reusableBytes.releaseLast(); 
        reporter.close();
    }
}
