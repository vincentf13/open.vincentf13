package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Progress;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

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
    private final OrderCreateDecoder createDecoder = new OrderCreateDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(512);
    private ExcerptTailer tailer;
    private boolean isReplaying = false;
    
    public Engine(OrderProcessor processor,
                  ExecutionReporter reporter,
                  Ledger ledger) {
        this.processor = processor;
        this.reporter = reporter;
        this.ledger = ledger;
    }
    
    @PostConstruct
    public void init() {
        start("core-matching-engine");
    }
    
    @Override
    protected void onStart() {
        this.tailer = Storage.self().commandQueue().createTailer();
        Progress saved = Storage.self().metadata().get(MetaDataKey.MACHING_ENGINE_POINT);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            progress.setOrderIdCounter(saved.getOrderIdCounter());
            progress.setTradeIdCounter(saved.getTradeIdCounter());
        } else {
            progress.setOrderIdCounter(1);
            progress.setTradeIdCounter(1);
        }
        
        log.info("正在恢復內存訂單簿狀態...");
        Storage.self().activeOrders().keySet().forEach(id -> {
            Order o = Storage.self().orders().get(id);
            if (o != null && o.getStatus() < 2)
                processor.rebuildIndex(o);
            else
                Storage.self().activeOrders().remove(id);
        });
        
        if (progress.getLastProcessedSeq() > 0) {
            isReplaying = true;
            tailer.moveToIndex(progress.getLastProcessedSeq());
        }
    }
    
    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            int msgType = wire.read(ChronicleWireKey.msgType).int32();
            long gwSeq = wire.read(ChronicleWireKey.gwSeq).int64();
            
            if (isReplaying && seq >= tailer.queue().lastIndex())
                isReplaying = false;
            
            reusableBytes.clear();
            wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
            payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int) reusableBytes.readRemaining());
            
            if (msgType == MsgType.AUTH)
                handleAuth(gwSeq);
            else if (msgType == MsgType.ORDER_CREATE)
                dispatchOrderCreate(gwSeq);
            
            progress.setLastProcessedSeq(seq);
            Storage.self().metadata().put(MetaDataKey.MACHING_ENGINE_POINT, progress);
        });
        if (!handled && isReplaying)
            isReplaying = false;
        return handled ? 1 : 0;
    }
    
    private void dispatchOrderCreate(long gwSeq) {
        SbeCodec.decode(payloadBuffer, 0, createDecoder);
        String cid = createDecoder.clientOrderId();
        CidKey key = new CidKey(createDecoder.userId(), cid);
        
        Long resId = Storage.self().cids().get(key);
        if (resId != null) {
            if (!isReplaying) {
                Order o = Storage.self().orders().get(resId);
                if (o != null)
                    reporter.resendReport(o, gwSeq);
            }
            return;
        }
        
        long orderId = progress.getOrderIdCounter();
        progress.setOrderIdCounter(orderId + 1);
        
        // 傳遞確定性的 tradeId 生成器
        processor.handleOrderCreate(createDecoder, gwSeq, orderId, cid, isReplaying, () -> {
            long tid = progress.getTradeIdCounter();
            progress.setTradeIdCounter(tid + 1);
            return tid;
        });
        
        Storage.self().cids().put(key, orderId);
    }
    
    private void handleAuth(long gwSeq) {
        // 認證訊息 Payload 目前僅包含 userId (由 WsHandler 寫入)
        // 由於 AUTH 不走 SBE (目前是 Wire 直接寫入)，我們從 Wire 讀取
        // 注意：doWork 已經 poll 了 payload，這裡需要從 wire 讀取 userId 欄位
        // 修正：handleAuth 應該直接接收已讀取的 userId
        long userId = payloadBuffer.getLong(0); // 假設認證 Payload 就是一個 Long
        ledger.initBalance(userId, Asset.BTC, gwSeq);
        ledger.initBalance(userId, Asset.USDT, gwSeq);
        reporter.sendAuthSuccess(userId, gwSeq, isReplaying);
    }
    
    @Override
    protected void onStop() {
        reusableBytes.releaseLast();
        reporter.close();
    }
}
