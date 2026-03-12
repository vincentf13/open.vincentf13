package open.vincentf13.service.spot.gateway.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.PointerBytesStore;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Gateway Aeron 接收器 (災難恢復增強版)
 職責：監聽來自核心引擎的回報數據，並主動上報進度實現握手
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

    @PostConstruct public void init() { start("gw-result-receiver"); }

    @Override
    protected void onStart() {
        subscription = aeron.addSubscription(AeronChannel.GATEWAY_URL, AeronChannel.DATA_STREAM_ID);
        controlPublication = aeron.addPublication(AeronChannel.MATCHING_URL, AeronChannel.CONTROL_STREAM_ID);
        
        Progress saved = Storage.self().metadata().get(MetaDataKey.GW_RECEVIER_POINT);
        if (saved != null) progress.setLastProcessedSeq(saved.getLastProcessedSeq());
        else progress.setLastProcessedSeq(-1L);
        
        currentState = AeronState.WAITING;
        log.info("AeronReceiver 啟動：當前進度: {}, 進入握手狀態...", progress.getLastProcessedSeq());
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !subscription.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("檢測到核心引擎連線中斷，回退至握手模式...");
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
            log.info("Gateway 回報鏈路同步成功");
        }

        if (gwSeq <= progress.getLastProcessedSeq()) return;

        long messageAddress = buffer.addressOffset() + offset;
        pointerBytesStore.set(messageAddress, length);
        
        Storage.self().resultQueue().acquireAppender().writeDocument(wire -> {
            wire.bytes().write(pointerBytesStore);
        });

        progress.setLastProcessedSeq(gwSeq);
        Storage.self().metadata().put(MetaDataKey.GW_RECEVIER_POINT, progress);
    };

    @Override
    protected void onStop() { 
        if (subscription != null) subscription.close();
        if (controlPublication != null) controlPublication.close();
    }
}
