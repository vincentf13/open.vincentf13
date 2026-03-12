package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;
import org.springframework.stereotype.Component;
import net.openhft.chronicle.bytes.PointerBytesStore;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Matching Core Aeron 接收器 (災難恢復增強版)
 職責：監聽來自 Gateway 的指令數據，並主動上報進度實現握手
 */
@Component
public class AeronReceiver extends Worker {
    private final Aeron aeron;
    private Subscription subscription;
    private Publication controlPublication;
    private final BufferClaim bufferClaim = new BufferClaim();
    private final Progress progress = new Progress();
    private final PointerBytesStore pointerBytesStore = new PointerBytesStore();
    
    private AeronState currentState = AeronState.WAITING;
    private long lastResumeSentTime = 0;

    public AeronReceiver(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct public void init() { start("core-command-receiver"); }

    @Override
    protected void onStart() {
        subscription = aeron.addSubscription(AeronChannel.MATCHING_URL, AeronChannel.DATA_STREAM_ID);
        controlPublication = aeron.addPublication(AeronChannel.GATEWAY_URL, AeronChannel.CONTROL_STREAM_ID);
        
        Progress saved = Storage.self().metadata().get(MetaDataKey.MACHING_RECEVIER_POINT);
        if (saved != null) progress.setLastProcessedSeq(saved.getLastProcessedSeq());
        else progress.setLastProcessedSeq(-1L);
        
        currentState = AeronState.WAITING; 
        log.info("AeronReceiver (Core) 啟動：當前進度: {}，進入握手狀態...", progress.getLastProcessedSeq());
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !subscription.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("Aeron 連線已中斷，回退至握手模式...");
        }

        if (currentState == AeronState.WAITING) {
            long now = System.currentTimeMillis();
            if (now - lastResumeSentTime > 500) {
                sendResumeSignalBlocking();
                lastResumeSentTime = now;
            }
        }
        return subscription.poll(fragmentHandler, 10);
    }

    private void sendResumeSignalBlocking() {
        AeronUtil.claimAndSend(controlPublication, bufferClaim, 12, idleStrategy, running, (buffer, offset) -> {
            buffer.putInt(offset, MsgType.RESUME);
            buffer.putLong(offset + 4, progress.getLastProcessedSeq());
        });
    }

    private final FragmentHandler fragmentHandler = (buffer, offset, length, header) -> {
        int msgType = buffer.getInt(offset);
        long gwSeq = buffer.getLong(offset + 4);
        
        if (currentState == AeronState.WAITING && gwSeq > progress.getLastProcessedSeq()) {
            currentState = AeronState.SENDING;
            log.info("已收到業務數據，握手成功，停止上報進度");
        }

        if (gwSeq <= progress.getLastProcessedSeq()) return;

        long messageAddress = buffer.addressOffset() + offset;
        Storage.self().commandQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(msgType);
            wire.write("gwSeq").int64(gwSeq);
            if (msgType == MsgType.AUTH) {
                wire.write("userId").int64(buffer.getLong(offset + 12));
            } else if (msgType == MsgType.ORDER_CREATE) {
                pointerBytesStore.set(messageAddress + 12, length - 12);
                wire.write("payload").bytes(pointerBytesStore);
            }
        });
        progress.setLastProcessedSeq(gwSeq);
        Storage.self().metadata().put(MetaDataKey.MACHING_RECEVIER_POINT, progress);
    };

    @Override
    protected void onStop() { 
        if (subscription != null) subscription.close();
        if (controlPublication != null) controlPublication.close();
    }
}
