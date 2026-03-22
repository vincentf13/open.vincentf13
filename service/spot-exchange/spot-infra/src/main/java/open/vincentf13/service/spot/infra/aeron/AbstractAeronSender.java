package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.MetricsCollector;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Aeron 發送器抽象基類 (Abstract Aeron Sender)
 職責：管理 Aeron 連線生命週期，並提供基於接收端反饋的 WAL 指令流發送機制
 */
@Slf4j
public abstract class AbstractAeronSender extends Worker {
    protected final Aeron aeron;
    protected final ChronicleQueue wal;
    
    protected final String dataUrl;
    protected final int dataStreamId;
    protected final String controlUrl;
    protected final int controlStreamId;

    protected Publication publication;
    protected Subscription controlSubscription;
    protected final BufferClaim bufferClaim = new BufferClaim();
    protected final org.agrona.concurrent.IdleStrategy idleStrategy = new org.agrona.concurrent.BackoffIdleStrategy(
        100, 10, 1, 1000 // 從 Spin 100 次開始，最後退避到 Park 1ms
    );

    protected AeronClient aeronClient;
    protected ExcerptTailer tailer;
    protected AeronState currentState = AeronState.WAITING;
    protected long backPressureCount = 0;

    // 預先定義 Reader 以避免在 doWork 中產生 Lambda 分配
    private final net.openhft.chronicle.wire.ReadMarshallable walReader = this::onWalMessage;

    public AbstractAeronSender(Aeron aeron, ChronicleQueue wal,
                               String dataUrl, int dataStreamId, String controlUrl, int controlStreamId) {
        this.aeron = aeron;
        this.wal = wal;
        this.dataUrl = dataUrl;
        this.dataStreamId = dataStreamId;
        this.controlUrl = controlUrl;
        this.controlStreamId = controlStreamId;
    }

    @Override
    protected void onStart() {
        this.publication = aeron.addPublication(dataUrl, dataStreamId);
        this.controlSubscription = aeron.addSubscription(controlUrl, controlStreamId);
        this.tailer = wal.createTailer();
        this.aeronClient = new AeronClient(publication, bufferClaim, idleStrategy, running);

        // 強制進入等待握手模式，完全依賴接收端的 RESUME 信號
        currentState = AeronState.WAITING;
        log.info("{} 啟動成功，等待 RESUME 信號以同步位點...", getClass().getSimpleName());
    }

    @Override
    protected int doWork() {
        // 1. 連線狀態檢查
        if (currentState == AeronState.SENDING && !publication.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("檢測到接收端斷開，回退至握手模式...");
        }

        int workDone = 0;
        // 2. 處理控制流 (優先處理握手與控制指令)
        workDone += controlSubscription.poll(resumeHandler, 10);
        
        if (currentState == AeronState.WAITING) {
            return workDone;
        }

        // 3. 處理 WAL 指令流發送 (批量優化：從 100 提高到 10000)
        int msgsSent = 0;
        for (int i = 0; i < 10000; i++) {
            if (tailer.readDocument(walReader)) {
                msgsSent++;
            } else {
                break;
            }
        }
        
        if (msgsSent > 0) {
            MetricsCollector.add(MetricsKey.AERON_SEND_COUNT, msgsSent);
            workDone += msgsSent;
        }

        if (workDone > 0 && backPressureCount > 1000) {
            log.warn("警告：偵測到嚴重背壓，請檢查接收端效能！");
            MetricsCollector.add(MetricsKey.AERON_BACKPRESSURE, backPressureCount);
            backPressureCount = 0;
        }

        return workDone;
    }

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset) == MsgType.RESUME) {
            if (currentState == AeronState.WAITING) {
                long resumeSeq = buffer.getLong(offset + 4);
                // 直接映射：Sequence 就是實體 WAL Index
                long walIndex = (resumeSeq == MSG_SEQ_NONE) ? WAL_INDEX_NONE : resumeSeq;
                
                log.info("✅ 握手成功！收到對端 RESUME 訊號，Sequence/Index: {}", walIndex);
                
                if (walIndex == WAL_INDEX_NONE) {
                    tailer.toStart();
                } else {
                    tailer.moveToIndex(walIndex);
                }
                currentState = AeronState.SENDING;
            }
        }
    };

    /**
     * 子類實現具體的 WAL 指令讀取與 Aeron 發送邏輯
     */
    protected abstract void onWalMessage(net.openhft.chronicle.wire.WireIn wire);

    @Override
    protected void onStop() {
        if (publication != null) publication.close();
        if (controlSubscription != null) controlSubscription.close();
        ThreadContext.cleanup();
    }
}
