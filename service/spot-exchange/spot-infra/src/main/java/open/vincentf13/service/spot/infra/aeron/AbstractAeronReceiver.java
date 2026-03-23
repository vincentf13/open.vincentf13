package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
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
    // 自定義處理器接口，支持 DirectBuffer (ReadOnly)
    @FunctionalInterface
    public interface AeronMessageHandler {
        void onMessage(int msgType, org.agrona.DirectBuffer buffer, int offset, int length);
    }

    protected final Aeron aeron;
    protected final ChronicleMap<Byte, MsgProgress> metadata;
    protected final byte metadataKey;
    protected final String subscriptionChannel;
    protected final int subscriptionStreamId;
    protected final String controlChannel;
    protected final int controlStreamId;

    protected Subscription subscription;
    protected Publication controlPublication;
    protected final BufferClaim bufferClaim = new BufferClaim();
    protected final org.agrona.concurrent.IdleStrategy idleStrategy = new org.agrona.concurrent.BusySpinIdleStrategy();
    protected final MsgProgress progress = new MsgProgress();
    
    // 使用 FragmentAssembler 確保跨 MTU 訊息的完整性 (處理網路分片)
    protected FragmentAssembler assembler;
    protected AeronClient controlClient;
    
    protected AeronState currentState = AeronState.WAITING;
    protected long lastResumeSentTime = 0;
    
    private long localPollCount = 0;
    private static final int METRICS_BATCH_SIZE = 5000;

    // 外部處理器，用於 Poll 模式
    private AeronMessageHandler externalHandler;

    public AbstractAeronReceiver(Aeron aeron,
                                 ChronicleMap<Byte, MsgProgress> metadata,
                                 byte metadataKey,
                                 String subscriptionChannel,
                                 int subscriptionStreamId,
                                 String controlChannel,
                                 int controlStreamId) {
        this.aeron = aeron;
        this.metadata = metadata;
        this.metadataKey = metadataKey;
        this.subscriptionChannel = subscriptionChannel;
        this.subscriptionStreamId = subscriptionStreamId;
        this.controlChannel = controlChannel;
        this.controlStreamId = controlStreamId;
    }

    /**
     * 被動模式下的手動初始化
     */
    public void setup() {
        onStart();
    }

    /**
     * 被動模式下的拉取入口
     */
    public int poll(AeronMessageHandler handler, int limit) {
        this.externalHandler = handler;
        
        if (currentState == AeronState.SENDING && !subscription.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("檢測到發送端斷開，回退至握手模式...");
        }

        if (currentState == AeronState.WAITING) {
            long now = System.currentTimeMillis();
            if (now - lastResumeSentTime > 200) {
                sendResumeSignalBlocking();
                lastResumeSentTime = now;
            }
        }
        
        updatePollMetrics();
        return subscription.poll(assembler, limit);
    }

    private void updatePollMetrics() {
        localPollCount++;
        if (localPollCount >= METRICS_BATCH_SIZE) {
            open.vincentf13.service.spot.infra.metrics.MetricsCollector.add(MetricsKey.POLL_COUNT, localPollCount);
            localPollCount = 0;
        }
    }

    @Override
    protected void onStart() {
        subscription = aeron.addSubscription(subscriptionChannel, subscriptionStreamId);
        controlPublication = aeron.addPublication(controlChannel, controlStreamId);
        
        // 初始化控制端客戶端 (用於發送 RESUME 信號)
        this.controlClient = new AeronClient(controlPublication, bufferClaim, idleStrategy, running);
        
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
        
        // 更新指標：記錄嘗試 Poll 的次數 (批次)
        localPollCount++;
        if (localPollCount >= METRICS_BATCH_SIZE) {
            open.vincentf13.service.spot.infra.metrics.MetricsCollector.add(MetricsKey.POLL_COUNT, localPollCount);
            localPollCount = 0;
        }

        // 關鍵：傳遞 assembler 而非直接傳遞 fragmentHandler
        int fragments = subscription.poll(assembler, 10);
        if (fragments > 0) {
            log.debug("[AERON-RECEIVER] 成功 Poll 到 {} 個數據片段，狀態: {}", fragments, currentState);
        }
        return fragments;
    }

    protected void sendResumeSignalBlocking() {
        controlClient.send(12, (buffer, offset) -> {
            buffer.putInt(offset, MsgType.RESUME);
            buffer.putLong(offset + 4, progress.getLastProcessedSeq());
        });
    }

    private void fragmentHandler(org.agrona.DirectBuffer buffer, int offset, int length, io.aeron.logbuffer.Header header) {
        // 按照 AbstractSbeModel.SEQ_OFFSET 定義，Seq 在第 4 個位元組之後
        final long msgSeq = buffer.getLong(offset + 4);
        final long lastSeq = progress.getLastProcessedSeq();
        
        // 1. 握手與自愈邏輯
        if (currentState == AeronState.WAITING) {
            // 只要收到的序號比本地小，且本地已經有一定進度，就判定為發送端重置了 (例如壓測重跑)
            if (msgSeq < lastSeq && lastSeq != MSG_SEQ_NONE) {
                log.warn("[AERON-RECEIVER] 檢測到發送端 Sequence 重置 (msgSeq={}, current={})，執行自動進度校準", msgSeq, lastSeq);
                progress.setLastProcessedSeq(MSG_SEQ_NONE);
                currentState = AeronState.SENDING;
            } else if (msgSeq > lastSeq) {
                currentState = AeronState.SENDING;
                log.info("已與發送端對齊進度 (msgSeq: {})，握手成功", msgSeq);
            } else if (msgSeq == lastSeq) {
                // 剛好相等，直接進入發送模式
                currentState = AeronState.SENDING;
            }
        }

        // 2. 冪等與連續性檢查
        if (msgSeq <= progress.getLastProcessedSeq()) {
            open.vincentf13.service.spot.infra.metrics.MetricsCollector.increment(MetricsKey.AERON_DROPPED_COUNT);
            return;
        }
        
        if (currentState == AeronState.SENDING && msgSeq != lastSeq + 1 && lastSeq != MSG_SEQ_NONE) {
            log.error("鏈路跳號！期望: {}, 實際: {}。將強制進入 WAITING 模式並重新握手。", lastSeq + 1, msgSeq);
            currentState = AeronState.WAITING;
            open.vincentf13.service.spot.infra.metrics.MetricsCollector.increment(MetricsKey.AERON_DROPPED_COUNT);
            sendResumeSignalBlocking();
            return;
        }

        // 3. 業務處理與落地
        if (externalHandler != null) {
            externalHandler.onMessage(buffer.getInt(offset), buffer, offset, length);
        } else {
            onMessage(buffer, offset, length);
        }
        
        progress.setLastProcessedSeq(msgSeq);
        
        // 定期存檔進度 (每 100 筆)
        if (msgSeq % 100 == 0) {
            metadata.put(metadataKey, progress);
        }
    }

    /**
     * 子類實現具體的訊息處理與 WAL 落地
     */
    protected abstract void onMessage(org.agrona.DirectBuffer buffer, int offset, int length);

    @Override
    protected void onStop() { 
        if (subscription != null) subscription.close();
        if (controlPublication != null) controlPublication.close();
        ThreadContext.cleanup();
    }
}
