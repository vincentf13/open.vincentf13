package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.infra.aeron.AeronConstants.AeronState;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Aeron 發送器抽象基類 
 職責：管理發送鏈路生命週期，處理基於 WAL 的指令重放與位點對齊。
 */
@Slf4j
public abstract class AbstractAeronSender extends Worker {
    protected final ChronicleQueue wal;
    protected Publication publication;
    protected Subscription controlSub;
    protected ExcerptTailer tailer;

    protected AeronState currentState = AeronState.WAITING;
    private long localSendCount = 0, lastHeartbeatTime = 0;

    public AbstractAeronSender(ChronicleQueue wal) { this.wal = wal; }

    /** 由子類提供通道配置並執行初始化 */
    protected void initChannels(String dataUrl, int dataId, String ctrlUrl, int ctrlId) {
        this.publication = AeronClientHolder.aeron().addPublication(dataUrl, dataId);
        this.controlSub = AeronClientHolder.aeron().addSubscription(ctrlUrl, ctrlId);
        this.tailer = wal.createTailer();
    }
    
    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !publication.isConnected()) currentState = AeronState.WAITING;

        int work = controlSub.poll(resumeHandler, AeronConstants.AERON_POLL_LIMIT);
        if (currentState == AeronState.WAITING) return work;

        for (int i = 0; i < AeronConstants.WAL_BATCH_SIZE; i++) {
            long lastIdx = tailer.index();
            try (var dc = tailer.readingDocument()) {
                if (!dc.isPresent()) break;
                onWalMessage(dc.wire());
                if (dc.wire().bytes().readRemaining() > 0) { // 發送失敗 (如背壓)
                    tailer.moveToIndex(lastIdx); break;
                }
                work++;
            }
        }
        
        if ((localSendCount += work) >= AeronConstants.METRICS_BATCH_SIZE) {
            open.vincentf13.service.spot.infra.metrics.MetricsCollector.add(MetricsKey.AERON_SEND_COUNT, localSendCount);
            localSendCount = 0;
        }

        long now = open.vincentf13.service.spot.infra.util.Clock.now();
        if (now - lastHeartbeatTime > 1000) {
            onHeartbeat();
            lastHeartbeatTime = now;
        }
        return work;
    }

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset) == MsgType.RESUME && currentState == AeronState.WAITING) {
            long walIndex = buffer.getLong(offset + AeronConstants.MSG_SEQ_OFFSET);
            log.info("✅ 握手成功！位點: {}", walIndex);
            if (walIndex == WAL_INDEX_NONE || walIndex == MSG_SEQ_NONE || !tailer.moveToIndex(walIndex)) tailer.toStart();
            else try (var dc = tailer.readingDocument()) {} 
            currentState = AeronState.SENDING;
        }
    };

    protected abstract void onWalMessage(net.openhft.chronicle.wire.WireIn wire);
    
    /** 每秒執行一次的指標或心跳邏輯 */
    protected void onHeartbeat() {}

    @Override
    protected void onStop() {
        if (publication != null) publication.close();
        if (controlSub != null) controlSub.close();
        ThreadContext.cleanup();
        AeronThreadContext.cleanup();
    }
}
