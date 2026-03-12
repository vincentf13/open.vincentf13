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
 職責：作為系統唯一的狀態機驅動者，負責指令路由、全局 Sequence 連續性斷言與重播模式管理
 */
@Slf4j
@Component
public class Engine extends Worker {
    /** 指令流 WAL：從 Gateway 傳輸至 Matching 的原始指令隊列，保證指令的物理順序 */
    private final ChronicleQueue gwToMatchingWal = Storage.self().gwToMatchingWal();
    
    /** 元數據存儲：持久化記錄處理位點，包含本地 Queue Index 與來源端業務 Sequence (gwSeq) */
    private final ChronicleMap<Byte, Progress> metadata = Storage.self().metadata();

    private final OrderProcessor orderProcessor;
    private final SystemProcessor systemProcessor;
    private final ExecutionReporter reporter;
    
    /** 內存進度快照：持有全局 ID 生成器，確保 OrderId 與 TradeId 在重播時的絕對一致性 */
    private final Progress progress = new Progress();
    private ExcerptTailer tailer;
    private boolean isReplaying = false;

    /** 零拷貝解碼緩衝區：直接包裹 Chronicle Queue 堆外內存地址，消除數據搬運開銷 */
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(512);

    public Engine(OrderProcessor orderProcessor, SystemProcessor systemProcessor, ExecutionReporter reporter) {
        this.orderProcessor = orderProcessor;
        this.systemProcessor = systemProcessor;
        this.reporter = reporter;
    }

    @PostConstruct public void init() { start("core-matching-engine"); }

    private void setReplaying(boolean val) {
        this.isReplaying = val;
        reporter.setReplaying(val);
    }

    @Override
    protected void onStart() {
        this.tailer = gwToMatchingWal.createTailer();
        
        Progress saved = metadata.get(MetaDataKey.MACHING_ENGINE_POINT);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            progress.setLastProcessedGwSeq(saved.getLastProcessedGwSeq());
            progress.setOrderIdCounter(saved.getOrderIdCounter());
            progress.setTradeIdCounter(saved.getTradeIdCounter());
        } else {
            progress.setOrderIdCounter(1); progress.setTradeIdCounter(1);
        }

        orderProcessor.rebuildState();

        if (progress.getLastProcessedSeq() > 0) {
            setReplaying(true); 
            tailer.moveToIndex(progress.getLastProcessedSeq());
            log.info("啟動重播恢復模式，位點: {}", progress.getLastProcessedSeq());
        } else {
            setReplaying(false);
        }
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            int msgType = wire.read(ChronicleWireKey.msgType).int32();
            long gwSeq = wire.read(ChronicleWireKey.gwSeq).int64();
            
            if (isReplaying && seq >= tailer.queue().lastIndex()) {
                setReplaying(false);
                log.info("狀態重播完成，切換至實時模式 (gwSeq: {})", gwSeq);
            }

            /** 
              全局連續性斷言 (Sequence Continuity Assertion)
              這是 Event Sourcing 架構的一致性生命線，確保指令流無空洞執行，防止財務狀態機因跳號而損壞
             */
            long lastSeq = progress.getLastProcessedGwSeq();
            if (lastSeq != -1) {
                if (gwSeq == lastSeq) return; 
                if (gwSeq != lastSeq + 1) {
                    log.error("指令跳號！期望: {}, 實際: {}。觸發安全停機。", lastSeq + 1, gwSeq);
                    System.exit(1); 
                }
            }

            reusableBytes.clear(); 
            wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
            payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());

            if (msgType == MsgType.AUTH) {
                systemProcessor.handleAuth(wire.read(ChronicleWireKey.userId).int64(), gwSeq);
            } else if (msgType == MsgType.ORDER_CREATE) {
                orderProcessor.processCreateCommand(payloadBuffer, gwSeq, this::nextOrderId, this::nextTradeId);
            }

            progress.setLastProcessedSeq(seq);
            progress.setLastProcessedGwSeq(gwSeq);
            metadata.put(MetaDataKey.MACHING_ENGINE_POINT, progress);
        });
        
        if (!handled && isReplaying) setReplaying(false);
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
