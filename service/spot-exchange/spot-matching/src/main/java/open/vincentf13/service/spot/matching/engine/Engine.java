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
 職責：指令隊列路由、重播模式切換、全局一致性檢查
 */
@Slf4j
@Component
public class Engine extends Worker {
    private final ChronicleQueue gwToMatchingWal = Storage.self().gwToMatchingWal();
    private final ChronicleMap<Byte, Progress> metadata = Storage.self().metadata();

    private final OrderProcessor orderProcessor;
    private final SystemProcessor systemProcessor;
    private final ExecutionReporter reporter;
    
    private final Progress progress = new Progress();
    private ExcerptTailer tailer;
    private boolean isReplaying = false;

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
            this.isReplaying = true;
            reporter.setReplaying(true); // 僅需在 Reporter 層級攔截輸出
            tailer.moveToIndex(progress.getLastProcessedSeq());
            log.info("啟動重播恢復模式，位點: {}", progress.getLastProcessedSeq());
        } else {
            reporter.setReplaying(false);
        }
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            int msgType = wire.read(ChronicleWireKey.msgType).int32();
            long gwSeq = wire.read(ChronicleWireKey.gwSeq).int64();
            
            // 判斷是否追上末尾，若追上則切換為實時模式
            if (isReplaying && seq >= tailer.queue().lastIndex()) {
                this.isReplaying = false;
                reporter.setReplaying(false);
                log.info("狀態恢復完成，進入實時模式 (gwSeq: {})", gwSeq);
            }

            // 連續性檢查
            long lastSeq = progress.getLastProcessedGwSeq();
            if (lastSeq != -1) {
                if (gwSeq == lastSeq) return; 
                if (gwSeq != lastSeq + 1) {
                    log.error("指令跳號！期望: {}, 實際: {}。", lastSeq + 1, gwSeq);
                    System.exit(1); 
                }
            }

            reusableBytes.clear(); 
            wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
            payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());

            // 路由分發 (Processor 現在是完全無狀態的於重播旗標)
            if (msgType == MsgType.AUTH) {
                systemProcessor.handleAuth(wire.read(ChronicleWireKey.userId).int64(), gwSeq);
            } else if (msgType == MsgType.ORDER_CREATE) {
                orderProcessor.processCreateCommand(payloadBuffer, gwSeq, this::nextOrderId, this::nextTradeId);
            }

            // 進度更新
            progress.setLastProcessedSeq(seq);
            progress.setLastProcessedGwSeq(gwSeq);
            metadata.put(MetaDataKey.MACHING_ENGINE_POINT, progress);
        });
        
        if (!handled && isReplaying) {
            isReplaying = false;
            reporter.setReplaying(false);
        }
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
