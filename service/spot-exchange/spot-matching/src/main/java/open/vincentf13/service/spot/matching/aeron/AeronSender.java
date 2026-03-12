package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Matching Core Aeron 發送器 (災難恢復增強版)
 職責：將成交回報與行情數據發送至 Gateway
 */
@Component
public class AeronSender extends Worker {
    private final Aeron aeron;
    private Publication publication;
    private Subscription controlSubscription;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();
    private final BufferClaim bufferClaim = new BufferClaim();
    private final UnsafeBuffer payloadWrapBuffer = new UnsafeBuffer(0, 0);

    private AeronState currentState = AeronState.WAITING;

    public AeronSender(Aeron aeron) {
        this.aeron = aeron;
    }

    /** 初始化並啟動工作執行緒 */
    @PostConstruct public void init() { start("core-result-sender"); }

    @Override
    protected void onStart() {
        // 1. 數據發送通道 (發送至 Gateway)
        publication = aeron.addPublication(AeronChannel.GATEWAY_URL, AeronChannel.DATA_STREAM_ID);
        // 2. 控制訊號監聽通道 (監聽來自 Gateway 的進度回報)
        controlSubscription = aeron.addSubscription(AeronChannel.MATCHING_URL, AeronChannel.CONTROL_STREAM_ID);
        
        tailer = Storage.self().resultQueue().createTailer();
        currentState = AeronState.WAITING;
        log.info("AeronSender (Core) 啟動，進入靜默等待狀態...");
    }

    @Override
    protected int doWork() {
        // 1. 活性檢查
        if (currentState == AeronState.SENDING && !publication.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("檢測到接收端 (Gateway) 連線中斷，回退至靜默等待模式...");
        }

        // 2. 監聽握手訊號
        int workDone = 0;
        if (currentState == AeronState.WAITING) {
            workDone = controlSubscription.poll(resumeHandler, 1);
        }

        // 3. 僅在 SENDING 狀態下執行數據發送
        if (currentState == AeronState.SENDING) {
            boolean handled = tailer.readDocument(wire -> {
                long seq = tailer.index();
                int msgType = wire.read(ChronicleWireKey.msgType).int32();
                // 提取核心本地生成的 matchingSeq
                long matchingSeq = wire.read(ChronicleWireKey.matchingSeq).int64();
                
                wire.read(ChronicleWireKey.payload).bytes(payload -> {
                    int payloadLength = (int) payload.readRemaining();
                    AeronUtil.claimAndSend(publication, bufferClaim, 12 + payloadLength, idleStrategy, running, (buffer, offset) -> {
                        buffer.putInt(offset, msgType);
                        buffer.putLong(offset + 4, matchingSeq); // 傳遞核心原始序號
                        payloadWrapBuffer.wrap(payload.addressForRead(payload.readPosition()), payloadLength);
                        buffer.putBytes(offset + 12, payloadWrapBuffer, 0, payloadLength);
                    });
                });
                
                progress.setLastProcessedSeq(seq);
                Storage.self().metadata().put(MetaDataKey.PK_CORE_RESULT_SENDER, progress);
            });
            if (handled) workDone++;
        }
        
        return workDone;
    }

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        int msgType = buffer.getInt(offset);
        if (msgType == MsgType.RESUME) {
            long lastProcessedIndex = buffer.getLong(offset + 4);
            log.info("收到 Gateway 握手訊號，執行跳轉至: {}", lastProcessedIndex);
            if (lastProcessedIndex == -1) tailer.toStart();
            else tailer.moveToIndex(lastProcessedIndex);
            currentState = AeronState.SENDING;
        }
    };

    @Override
    protected void onStop() { 
        if (publication != null) publication.close();
        if (controlSubscription != null) controlSubscription.close();
    }
}
