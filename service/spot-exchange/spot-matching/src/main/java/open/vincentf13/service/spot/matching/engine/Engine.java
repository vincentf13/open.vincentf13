package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.ReadMarshallable;
import net.openhft.chronicle.wire.WireIn;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.*;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 撮合引擎執行緒 (Engine Orchestrator)
 職責：指令路由、狀態管理與一致性檢查，實現熱點路徑零物件分配
 */
@Slf4j
@Component
public class Engine extends Worker implements ReadMarshallable {
    private final ChronicleQueue gwToMatchingWal = Storage.self().gwToMatchingWal();
    private final ChronicleMap<Byte, Progress> metadata = Storage.self().metadata();

    private final OrderProcessor orderProcessor;
    private final AuthProcessor authProcessor;
    private final DepositProcessor depositProcessor;
    private final ExecutionReporter reporter;
    
    private final Progress progress = new Progress();
    private ExcerptTailer tailer;
    private boolean isReplaying = false;

    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(512);

    public Engine(OrderProcessor orderProcessor, AuthProcessor authProcessor, DepositProcessor depositProcessor, ExecutionReporter reporter) {
        this.orderProcessor = orderProcessor;
        this.authProcessor = authProcessor;
        this.depositProcessor = depositProcessor;
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
            log.info("從元數據恢復位點: WAL={}, gwSeq={}, OrderId={}, TradeId={}", 
                progress.getLastProcessedSeq(), progress.getLastProcessedGwSeq(),
                progress.getOrderIdCounter(), progress.getTradeIdCounter());
        } else {
            log.warn("元數據不存在，將進行冷啟動初始化...");
            progress.setOrderIdCounter(1); progress.setTradeIdCounter(1);
            progress.setLastProcessedSeq(-1L);
            progress.setLastProcessedGwSeq(-1L);
        }

        orderProcessor.rebuildState();

        // 自愈機制：如果檢測到重播，啟動屏障保護
        if (progress.getLastProcessedSeq() > 0) {
            setReplaying(true); 
            tailer.moveToIndex(progress.getLastProcessedSeq());
            log.info("✅ 引擎狀態自愈啟動：正在重播 WAL 以校準記憶體狀態...");
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
        final long seq = tailer.index();
        final int msgType = wire.read(ChronicleWireKey.msgType).int32();
        final long gwSeq = wire.read(ChronicleWireKey.gwSeq).int64();
        
        // 狀態切換
        if (isReplaying && seq >= tailer.queue().lastIndex()) {
            setReplaying(false);
            log.info("重播完成，切換至實時模式 (gwSeq: {})", gwSeq);
        }

        // 連續性斷言
        final long lastSeq = progress.getLastProcessedGwSeq();
        if (lastSeq != -1) {
            if (gwSeq == lastSeq) return; 
            if (gwSeq != lastSeq + 1) {
                log.error("指令跳號！期望: {}, 實際: {}。請檢查 WAL 連續性或使用 ENGINE_FORCE_START=true 強制啟動。", lastSeq + 1, gwSeq);
                if (!"true".equalsIgnoreCase(System.getProperty("ENGINE_FORCE_START"))) {
                    System.exit(1); 
                }
            }
        }

        // 路由分發 (補全 DEPOSIT)
        switch (msgType) {
            case MsgType.AUTH -> authProcessor.handleAuth(wire.read(ChronicleWireKey.userId).int64(), gwSeq);
            case MsgType.ORDER_CREATE -> {
                reusableBytes.clear(); 
                wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
                payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());
                orderProcessor.processCreateCommand(payloadBuffer, gwSeq, this::nextOrderId, this::nextTradeId);
            }
            case MsgType.DEPOSIT -> {
                long uid = wire.read(ChronicleWireKey.userId).int64();
                int assetId = wire.read(ChronicleWireKey.topic).int32(); // 借用 topic 傳遞 assetId
                long amount = wire.read(ChronicleWireKey.data).int64(); // 借用 data 傳遞 amount
                depositProcessor.handleDeposit(uid, assetId, amount, gwSeq);
            }
        }

        // 進度存檔 (優化：每 100 條存檔一次，降低 IO 壓力)
        progress.setLastProcessedSeq(seq);
        progress.setLastProcessedGwSeq(gwSeq);
        if (seq % 100 == 0) {
            metadata.put(MetaDataKey.MACHING_ENGINE_POINT, progress);
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
        reusableBytes.releaseLast(); 
        reporter.close();
    }
}
