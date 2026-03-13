package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.ReadMarshallable;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.model.WalProgress;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Aeron 發送器抽象基類 (Abstract Aeron Sender)
 職責：管理 Aeron 連線生命週期，並提供 WAL 指令流的讀取、發送與進度追蹤機制
 */
@Slf4j
public abstract class AbstractAeronSender extends Worker implements ReadMarshallable {
    protected final Aeron aeron;
    protected final ChronicleQueue wal;
    protected final ChronicleMap<Byte, WalProgress> metadata;
    protected final byte metadataKey;
    
    protected final String dataUrl;
    protected final int dataStreamId;
    protected final String controlUrl;
    protected final int controlStreamId;

    protected Publication publication;
    protected Subscription controlSubscription;
    protected final BufferClaim bufferClaim = new BufferClaim();
    protected final IdleStrategy idleStrategy = new BackoffIdleStrategy();
    
    protected AeronClient aeronClient;
    protected ExcerptTailer tailer;
    protected final WalProgress progress = new WalProgress();
    protected AeronState currentState = AeronState.WAITING;
    protected long backPressureCount = 0;

    public AbstractAeronSender(Aeron aeron, ChronicleQueue wal, ChronicleMap<Byte, WalProgress> metadata, byte metadataKey,
                               String dataUrl, int dataStreamId, String controlUrl, int controlStreamId) {
        this.aeron = aeron;
        this.wal = wal;
        this.metadata = metadata;
        this.metadataKey = metadataKey;
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

        // 恢復上次處理進度 (作為參考)
        WalProgress saved = metadata.get(metadataKey);
        if (saved != null) {
            progress.setLastProcessedIndex(saved.getLastProcessedIndex());
            tailer.moveToIndex(progress.getLastProcessedIndex());
            log.info("{} 恢復本地位點: Index={}，等待 RESUME 信號...", getClass().getSimpleName(), progress.getLastProcessedIndex());
        } else {
            tailer.toStart();
        }

        currentState = AeronState.WAITING;
        log.info("{} 啟動成功，DataUrl={}, ControlUrl={}", getClass().getSimpleName(), dataUrl, controlUrl);
    }

    @Override
    protected int doWork() {
        // 1. 連線狀態檢查
        if (currentState == AeronState.SENDING && !publication.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("檢測到接收端斷開，回退至握手模式...");
        }

        int workDone = 0;
        // 2. 處理控制流 (握手與實時控制回報)
        workDone += controlSubscription.poll(resumeHandler, 10);
        
        if (currentState == AeronState.WAITING) {
            return workDone;
        }

        // 3. 處理 WAL 指令流 (批量發送優化)
        for (int i = 0; i < 100; i++) {
            if (tailer.readDocument(this)) {
                workDone++;
                
                // 每 100 筆存檔位點
                if (tailer.index() % 100 == 0) {
                    final long index = tailer.index();
                    progress.setLastProcessedIndex(index);
                    metadata.put(metadataKey, progress);
                }
            } else {
                break;
            }
        }

        if (workDone > 0 && backPressureCount > 1000) {
            log.warn("警告：偵測到嚴重背壓，請檢查接收端效能！");
            backPressureCount = 0;
        }

        return workDone;
    }

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset) == MsgType.RESUME) {
            long lastProcessedIndex = buffer.getLong(offset + 4);
            log.info("收到 RESUME 握手訊號，執行跳轉至: {}", lastProcessedIndex);
            
            if (lastProcessedIndex == WAL_INDEX_NONE) {
                tailer.toStart();
            } else {
                tailer.moveToIndex(lastProcessedIndex);
            }
            
            currentState = AeronState.SENDING;
        }
    };

    @Override
    protected void onStop() {
        if (publication != null) publication.close();
        if (controlSubscription != null) controlSubscription.close();
        ThreadContext.cleanup();
    }
}
