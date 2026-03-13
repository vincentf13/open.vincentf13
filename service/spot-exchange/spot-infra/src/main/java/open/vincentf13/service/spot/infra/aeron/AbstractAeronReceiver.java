package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.queue.ChronicleQueue;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.model.MsgProgress;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 抽象 Aeron 接收基類
 * 整合 Aeron 接收、進度恢復 (metadata)、RESUME 信號定期發送與冪等檢查邏輯
 */
@Slf4j
public abstract class AbstractAeronReceiver extends Worker {
    protected final Aeron aeron;
    protected final ChronicleQueue wal;
    protected final ChronicleMap<Byte, MsgProgress> metadata;
    protected final byte metadataKey;
    protected final String subscriptionChannel;
    protected final int subscriptionStreamId;
    protected final String controlChannel;
    protected final int controlStreamId;

    protected Subscription subscription;
    protected Publication controlPublication;
    protected final BufferClaim bufferClaim = new BufferClaim();
    protected final MsgProgress progress = new MsgProgress();
    
    // 使用 FragmentAssembler 確保跨 MTU 訊息的完整性 (處理網路分片)
    protected FragmentAssembler assembler;
    
    protected AeronState currentState = AeronState.WAITING;
    protected long lastResumeSentTime = 0;

    public AbstractAeronReceiver(Aeron aeron,
                                 ChronicleQueue wal,
                                 ChronicleMap<Byte, MsgProgress> metadata,
                                 byte metadataKey,
                                 String subscriptionChannel,
                                 int subscriptionStreamId,
                                 String controlChannel,
                                 int controlStreamId) {
        this.aeron = aeron;
        this.wal = wal;
        this.metadata = metadata;
        this.metadataKey = metadataKey;
        this.subscriptionChannel = subscriptionChannel;
        this.subscriptionStreamId = subscriptionStreamId;
        this.controlChannel = controlChannel;
        this.controlStreamId = controlStreamId;
    }

    @Override
    protected void onStart() {
        subscription = aeron.addSubscription(subscriptionChannel, subscriptionStreamId);
        controlPublication = aeron.addPublication(controlChannel, controlStreamId);
        
        // 初始化重組器：包裝原始 handler
        this.assembler = new FragmentAssembler(this::fragmentHandler);
        
        MsgProgress saved = metadata.get(metadataKey);
        if (saved != null) progress.setLastProcessedSeq(saved.getLastProcessedSeq());
        else progress.setLastProcessedSeq(MSG_SEQ_NONE);

        currentState = AeronState.WAITING;
        log.info("{} 啟動：當前進度: {}，進入握手狀態...", getClass().getSimpleName(), progress.getLastProcessedSeq());
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !subscription.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("檢測到發送端斷開，回退至握手模式...");
        }

        if (currentState == AeronState.WAITING) {
            long now = System.currentTimeMillis();
            if (now - lastResumeSentTime > 200) { // 縮短至 200ms
                sendResumeSignalBlocking();
                lastResumeSentTime = now;
            }
        }
        // 關鍵：傳遞 assembler 而非直接傳遞 fragmentHandler
        return subscription.poll(assembler, 10);
    }

    protected void sendResumeSignalBlocking() {
        AeronUtil.claimAndSend(controlPublication, bufferClaim, 12, idleStrategy, running, (buffer, offset) -> {
            buffer.putInt(offset, MsgType.RESUME);
            buffer.putLong(offset + 4, progress.getLastProcessedSeq());
        });
    }

    private void fragmentHandler(org.agrona.DirectBuffer buffer, int offset, int length, io.aeron.logbuffer.Header header) {
        final int msgType = buffer.getInt(offset);
        final long seq = buffer.getLong(offset + 4);
        final long lastSeq = progress.getLastProcessedSeq();
        
        // 1. 握手與自愈邏輯
        if (currentState == AeronState.WAITING) {
            if (seq == 0 && lastSeq > 1000) {
                log.warn("檢測到發送端 WAL 重置 (seq=0, lastProcessed={})，執行進度重置", lastSeq);
                progress.setLastProcessedSeq(MSG_SEQ_NONE);
            } else if (seq > lastSeq) {
                currentState = AeronState.SENDING;
                log.info("已與發送端對齊進度 (seq: {})，握手成功", seq);
            }
        }

        // 2. 冪等與連續性檢查
        if (seq <= progress.getLastProcessedSeq()) return;
        
        if (currentState == AeronState.SENDING && seq != lastSeq + 1 && lastSeq != MSG_SEQ_NONE) {
            log.error("鏈路跳號！期望: {}, 實際: {}。將強制對齊位點。", lastSeq + 1, seq);
        }

        // 3. 業務處理與落地 (由子類實作)
        onMessage(buffer, offset, length, msgType, seq);
        
        progress.setLastProcessedSeq(seq);
        metadata.put(metadataKey, progress);
    }

    /**
     * 子類實現具體的訊息處理與 WAL 落地
     */
    protected abstract void onMessage(org.agrona.DirectBuffer buffer, int offset, int length, int msgType, long seq);

    @Override
    protected void onStop() { 
        if (subscription != null) subscription.close();
        if (controlPublication != null) controlPublication.close();
        ThreadContext.cleanup();
    }
}
