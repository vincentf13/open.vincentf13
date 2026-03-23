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
    protected final org.agrona.concurrent.IdleStrategy idleStrategy = new org.agrona.concurrent.BusySpinIdleStrategy();

    protected AeronClient aeronClient;
    protected ExcerptTailer tailer;
    protected AeronState currentState = AeronState.WAITING;
    protected long backPressureCount = 0;
    private long localAeronSendCount = 0;
    private static final int METRICS_BATCH_SIZE = 5000;

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
        if (currentState == AeronState.SENDING && !publication.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("檢測到接收端斷開，回退至握手模式...");
        }

        int workDone = 0;
        workDone += controlSubscription.poll(resumeHandler, 10);
        
        if (currentState == AeronState.WAITING) {
            return workDone;
        }

        // 批量發送優化
        for (int i = 0; i < 1000; i++) {
            boolean failed = false;
            long indexBeforeRead = 0;
            try (var dc = tailer.readingDocument()) {
                if (!dc.isPresent()) break;
                
                indexBeforeRead = dc.index();
                onWalMessage(dc.wire());
                
                // 檢查是否發送成功 (透過 bytes 剩餘量判定，由 AeronSender.onWalMessage 操作)
                if (dc.wire().bytes().readRemaining() > 0) {
                    failed = true;
                } else {
                    workDone++;
                }
            }
            
            if (failed) {
                // 發送失敗 (如背壓)，在 dc 關閉後回退 tailer 位點，下次 doWork 重新處理
                tailer.moveToIndex(indexBeforeRead);
                break;
            }
        }
        
        if (workDone > 0) {
            localAeronSendCount += workDone;
            if (localAeronSendCount >= METRICS_BATCH_SIZE) {
                open.vincentf13.service.spot.infra.metrics.MetricsCollector.add(MetricsKey.AERON_SEND_COUNT, localAeronSendCount);
                localAeronSendCount = 0;
            }
        }

        return workDone;
    }

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset) == MsgType.RESUME) {
            if (currentState == AeronState.WAITING) {
                long walIndex = buffer.getLong(offset + 4);
                log.info("✅ 握手成功！收到對端 RESUME 訊號，請求位點: {}", walIndex);
                
                if (walIndex == WAL_INDEX_NONE || walIndex == MSG_SEQ_NONE) {
                    tailer.toStart();
                } else {
                    // 移動到最後處理過的位點，然後執行一次讀取來跳過它
                    if (tailer.moveToIndex(walIndex)) {
                        try (var dc = tailer.readingDocument()) { /* skip processed */ }
                    } else {
                        log.error("無法移動到指定位點: {}，回退至 Start", walIndex);
                        tailer.toStart();
                    }
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
