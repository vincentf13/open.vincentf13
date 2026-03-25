package open.vincentf13.service.spot.infra.aeron;

import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.model.MsgProgress;
import open.vincentf13.service.spot.infra.aeron.AeronConstants.AeronState;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 抽象 Aeron 接收基類 */
@Slf4j
public abstract class AbstractAeronReceiver extends Worker {
    @FunctionalInterface
    public interface AeronMessageHandler {
        void onMessage(int msgType, org.agrona.DirectBuffer buffer, int offset, int length);
    }

    protected final ChronicleMap<Byte, MsgProgress> metadata;
    protected final byte metadataKey;
    protected final String subUrl, controlUrl;
    protected final int subStreamId, controlStreamId;

    protected Subscription subscription;
    protected Publication controlPub;
    protected final MsgProgress progress = new MsgProgress();
    protected FragmentAssembler assembler;
    
    protected AeronState currentState = AeronState.WAITING;
    protected long lastResumeTime = 0;
    private long localPollCount = 0;
    private AeronMessageHandler externalHandler;

    public AbstractAeronReceiver(ChronicleMap<Byte, MsgProgress> metadata, byte metadataKey,
                                 String subUrl, int subStreamId, String controlUrl, int controlStreamId) {
        this.metadata = metadata; this.metadataKey = metadataKey;
        this.subUrl = subUrl; this.subStreamId = subStreamId;
        this.controlUrl = controlUrl; this.controlStreamId = controlStreamId;
    }

    public void setup() { running.set(true); onStart(); }

    public int poll(AeronMessageHandler handler, int limit) {
        this.externalHandler = handler;
        if (currentState == AeronState.SENDING && !subscription.isConnected()) currentState = AeronState.WAITING;
        if (currentState == AeronState.WAITING && System.currentTimeMillis() - lastResumeTime > AeronConstants.RESUME_SIGNAL_INTERVAL_MS) sendResume();
        
        int done = subscription.poll(assembler, limit);
        if ((localPollCount += 1) >= AeronConstants.METRICS_BATCH_SIZE) {
            open.vincentf13.service.spot.infra.metrics.MetricsCollector.add(MetricsKey.POLL_COUNT, localPollCount);
            localPollCount = 0;
        }
        return done;
    }

    @Override
    protected void onStart() {
        subscription = AeronClientHolder.aeron().addSubscription(subUrl, subStreamId);
        controlPub = AeronClientHolder.aeron().addPublication(controlUrl, controlStreamId);
        assembler = new FragmentAssembler(this::fragmentHandler);
        
        MsgProgress saved = metadata.get(metadataKey);
        progress.setLastProcessedSeq(saved != null ? saved.getLastProcessedSeq() : MSG_SEQ_NONE);
        currentState = AeronState.WAITING;
        log.info("{} 啟動，進度: {}，握手中...", getClass().getSimpleName(), progress.getLastProcessedSeq());
        sendResume();
    }

    @Override protected int doWork() { return poll(null, AeronConstants.AERON_POLL_LIMIT); }

    protected void sendResume() {
        AeronUtil.send(controlPub, AeronConstants.RESUME_SIGNAL_LENGTH, (buffer, offset) -> {
            buffer.putInt(offset, MsgType.RESUME);
            buffer.putLong(offset + AeronConstants.MSG_SEQ_OFFSET, progress.getLastProcessedSeq());
        }, running);
        lastResumeTime = System.currentTimeMillis();
    }

    private void fragmentHandler(org.agrona.DirectBuffer buffer, int offset, int length, io.aeron.logbuffer.Header header) {
        final long msgSeq = buffer.getLong(offset + AeronConstants.MSG_SEQ_OFFSET), lastSeq = progress.getLastProcessedSeq();
        
        if (currentState == AeronState.WAITING) {
            if (msgSeq < lastSeq && lastSeq != MSG_SEQ_NONE) progress.setLastProcessedSeq(MSG_SEQ_NONE);
            currentState = AeronState.SENDING;
        }

        if (msgSeq <= progress.getLastProcessedSeq()) return;
        
        if (msgSeq != lastSeq + 1 && lastSeq != MSG_SEQ_NONE) {
            log.error("鏈路跳號！期望: {}, 實際: {}", lastSeq + 1, msgSeq);
            currentState = AeronState.WAITING; sendResume(); return;
        }

        if (externalHandler != null) externalHandler.onMessage(buffer.getInt(offset), buffer, offset, length);
        else onMessage(buffer, offset, length);
        
        progress.setLastProcessedSeq(msgSeq);
        if (msgSeq % AeronConstants.METADATA_FLUSH_PERIOD == 0) metadata.put(metadataKey, progress);
    }

    protected abstract void onMessage(org.agrona.DirectBuffer buffer, int offset, int length);

    @Override
    protected void onStop() {
        if (subscription != null) subscription.close();
        if (controlPub != null) controlPub.close();
        ThreadContext.cleanup();
        AeronThreadContext.cleanup();
    }
}
