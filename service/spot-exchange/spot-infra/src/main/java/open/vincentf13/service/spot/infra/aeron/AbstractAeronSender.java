package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.ReadMarshallable;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 抽象 Aeron 發送基類
 * 整合 WAL 讀取、Aeron 發送、RESUME 信號接收與位點跳轉邏輯
 */
@Slf4j
public abstract class AbstractAeronSender extends Worker implements ReadMarshallable {
    protected final Aeron aeron;
    protected final ChronicleQueue wal;
    protected final String publicationChannel;
    protected final int publicationStreamId;
    protected final String subscriptionChannel;
    protected final int subscriptionStreamId;

    protected Publication publication;
    protected Subscription controlSubscription;
    protected ExcerptTailer tailer;
    protected final BufferClaim bufferClaim = new BufferClaim();
    protected AeronState currentState = AeronState.WAITING;
    protected long backPressureCount = 0;

    public AbstractAeronSender(Aeron aeron, 
                               ChronicleQueue wal, 
                               String publicationChannel, 
                               int publicationStreamId, 
                               String subscriptionChannel, 
                               int subscriptionStreamId) {
        this.aeron = aeron;
        this.wal = wal;
        this.publicationChannel = publicationChannel;
        this.publicationStreamId = publicationStreamId;
        this.subscriptionChannel = subscriptionChannel;
        this.subscriptionStreamId = subscriptionStreamId;
    }

    @Override
    protected void onStart() {
        publication = aeron.addPublication(publicationChannel, publicationStreamId);
        controlSubscription = aeron.addSubscription(subscriptionChannel, subscriptionStreamId);
        tailer = wal.createTailer();
        currentState = AeronState.WAITING;
        log.info("{} 啟動成功，等待 RESUME 信號...", getClass().getSimpleName());
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !publication.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("檢測到接收端斷開，回退至靜默等待模式...");
        }

        int workDone = 0;
        if (currentState == AeronState.WAITING) {
            workDone = controlSubscription.poll(resumeHandler, 1);
        }

        if (currentState == AeronState.SENDING) {
            // 批量發送優化：一次工作周期處理最多 100 條訊息
            for (int i = 0; i < 100; i++) {
                if (tailer.readDocument(this)) {
                    workDone++;
                } else {
                    break;
                }
            }
            
            if (workDone > 0 && backPressureCount > 1000) {
                log.warn("警告：偵測到嚴重背壓，請檢查接收端效能！");
                backPressureCount = 0;
            }
        }
        return workDone;
    }

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset) == MsgType.RESUME) {
            long lastProcessedIndex = buffer.getLong(offset + 4);
            log.info("收到握手訊號，執行跳轉至: {}", lastProcessedIndex);
            if (lastProcessedIndex == WAL_INDEX_NONE) tailer.toStart();
            else tailer.moveToIndex(lastProcessedIndex);
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
