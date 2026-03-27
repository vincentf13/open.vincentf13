package open.vincentf13.service.spot.infra.aeron;

import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.model.MsgProgress;
import open.vincentf13.service.spot.infra.aeron.AeronConstants.AeronState;
import open.vincentf13.service.spot.infra.util.Clock;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 抽象 Aeron 接收基類 */
@Slf4j
public abstract class AbstractAeronReceiver extends Worker {
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

    public AbstractAeronReceiver(ChronicleMap<Byte, MsgProgress> metadata, byte metadataKey,
                                 String subUrl, int subStreamId, String controlUrl, int controlStreamId) {
        this.metadata = metadata; this.metadataKey = metadataKey;
        this.subUrl = subUrl; this.subStreamId = subStreamId;
        this.controlUrl = controlUrl; this.controlStreamId = controlStreamId;
    }

    public int poll(int limit) {
        if (currentState == AeronState.SENDING && !subscription.isConnected()) currentState = AeronState.WAITING;
        if (currentState == AeronState.WAITING && Clock.now() - lastResumeTime > AeronConstants.RESUME_SIGNAL_INTERVAL_MS) sendResume();
        
        int done = subscription.poll(assembler, limit);
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

    @Override protected int doWork() { return poll(AeronConstants.AERON_POLL_LIMIT); }

    protected void sendResume() {
        AeronUtil.send(controlPub, AeronConstants.RESUME_SIGNAL_LENGTH, (buffer, offset) -> {
            buffer.putInt(offset, MsgType.RESUME);
            buffer.putLong(offset + AeronConstants.MSG_SEQ_OFFSET, progress.getLastProcessedSeq());
        }, running);
        lastResumeTime = Clock.now();
    }

    private void fragmentHandler(org.agrona.DirectBuffer buffer, int offset, int length, io.aeron.logbuffer.Header header) {
        final long msgSeq = buffer.getLong(offset + AeronConstants.MSG_SEQ_OFFSET);
        final long lastSeq = progress.getLastProcessedSeq();

        if (!readyToConsume(msgSeq, lastSeq)) return;
        if (msgSeq <= progress.getLastProcessedSeq()) return;

        if (msgSeq != lastSeq + 1 && lastSeq != MSG_SEQ_NONE) {
            log.error("鏈路跳號！期望: {}, 實際: {}", lastSeq + 1, msgSeq);
            currentState = AeronState.WAITING; sendResume(); return;
        }

        onMessage(buffer, offset, length);
        
        progress.setLastProcessedSeq(msgSeq);
        if (msgSeq % AeronConstants.METADATA_FLUSH_PERIOD == 0) metadata.put(metadataKey, progress);
    }

    private boolean readyToConsume(long msgSeq, long lastSeq) {
        if (currentState != AeronState.WAITING) return true;
        if (lastSeq == MSG_SEQ_NONE || msgSeq == lastSeq + 1) {
            currentState = AeronState.SENDING;
            return true;
        }
        if (msgSeq <= lastSeq) {
            sendResume();
            return false;
        }

        log.warn("恢復期間收到超前訊息，等待重送。期望: {}, 實際: {}", lastSeq + 1, msgSeq);
        sendResume();
        return false;
    }

    protected abstract void onMessage(org.agrona.DirectBuffer buffer, int offset, int length);

    @Override
    protected void onStop() {
        metadata.put(metadataKey, progress);
        if (subscription != null) subscription.close();
        if (controlPub != null) controlPub.close();
        ThreadContext.cleanup();
        AeronThreadContext.cleanup();
    }
}
