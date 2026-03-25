package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.thread.ThreadContext;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Aeron 發送器抽象基類
 職責：管理 Aeron 生命週期，負責 WAL 指令流發送與位點對齊。
 */
@Slf4j
public abstract class AbstractAeronSender extends Worker {
    protected final Aeron aeron;
    protected final ChronicleQueue wal;
    protected final String dataUrl, controlUrl;
    protected final int dataStreamId, controlStreamId;

    protected Publication publication;
    protected Subscription controlSub;
    protected ExcerptTailer tailer;

    protected AeronState currentState = AeronState.WAITING;
    private long localSendCount = 0;

    public AbstractAeronSender(Aeron aeron, ChronicleQueue wal, String dataUrl, int dataStreamId, String controlUrl, int controlStreamId) {
        this.aeron = aeron; this.wal = wal;
        this.dataUrl = dataUrl; this.dataStreamId = dataStreamId;
        this.controlUrl = controlUrl; this.controlStreamId = controlStreamId;
    }

    @Override
    protected void onStart() {
        this.publication = aeron.addPublication(dataUrl, dataStreamId);
        this.controlSub = aeron.addSubscription(controlUrl, controlStreamId);
        this.tailer = wal.createTailer();
        currentState = AeronState.WAITING;
        log.info("{} 啟動，等待握手訊號...", getClass().getSimpleName());
    }

    protected int send(int length, AeronUtil.AeronHandler handler) {
        return AeronUtil.send(publication, length, handler, running);
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !publication.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("接收端斷開，回退至握手模式...");
        }

        int work = controlSub.poll(resumeHandler, Config.AERON_POLL_LIMIT);
        if (currentState == AeronState.WAITING) return work;

        for (int i = 0; i < Config.WAL_BATCH_SIZE; i++) {
            long lastIdx = tailer.index();
            try (var dc = tailer.readingDocument()) {
                if (!dc.isPresent()) break;
                onWalMessage(dc.wire());
                if (dc.wire().bytes().readRemaining() > 0) { // 發送失敗 (如背壓)
                    tailer.moveToIndex(lastIdx);
                    break;
                }
                work++;
            }
        }
        
        if ((localSendCount += work) >= Config.METRICS_BATCH_SIZE) {
            open.vincentf13.service.spot.infra.metrics.MetricsCollector.add(MetricsKey.AERON_SEND_COUNT, localSendCount);
            localSendCount = 0;
        }
        return work;
    }

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset) == MsgType.RESUME && currentState == AeronState.WAITING) {
            long walIndex = buffer.getLong(offset + Config.MSG_SEQ_OFFSET);
            log.info("✅ 握手成功！恢復位點: {}", walIndex);
            if (walIndex == WAL_INDEX_NONE || walIndex == MSG_SEQ_NONE || !tailer.moveToIndex(walIndex)) {
                tailer.toStart();
            } else {
                try (var dc = tailer.readingDocument()) {} // skip processed
            }
            currentState = AeronState.SENDING;
        }
    };

    protected abstract void onWalMessage(net.openhft.chronicle.wire.WireIn wire);

    @Override
    protected void onStop() {
        if (publication != null) publication.close();
        if (controlSub != null) controlSub.close();
        ThreadContext.cleanup();
        AeronThreadContext.cleanup();
    }
}
