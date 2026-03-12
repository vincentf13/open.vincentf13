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
 職責：作為系統唯一的狀態機驅動者，負責指令路由、全局 Sequence 連貫性檢查與重播管理
 */
@Slf4j
@Component
public class Engine extends Worker {
    private final ChronicleQueue gwToMatchingWal = Storage.self().gwToMatchingWal();
    private final ChronicleMap<Byte, Progress> metadata = Storage.self().metadata();

    private final OrderProcessor orderProcessor;
    private final AuthProcessor authProcessor;
    private final ExecutionReporter reporter;
    
    private final Progress progress = new Progress();
    private ExcerptTailer tailer;
    private boolean isReplaying = false;

    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(512);

    public Engine(OrderProcessor orderProcessor, AuthProcessor authProcessor, ExecutionReporter reporter) {
        this.orderProcessor = orderProcessor;
        this.authProcessor = authProcessor;
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
                authProcessor.handleAuth(wire.read(ChronicleWireKey.userId).int64(), gwSeq);
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
