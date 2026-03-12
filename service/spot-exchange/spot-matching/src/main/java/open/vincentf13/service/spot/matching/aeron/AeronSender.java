package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.WireIn;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Matching Core Aeron 發送器
 職責：讀取回報流並發送至 Gateway，實現熱點路徑零物件分配
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AeronSender extends Worker implements net.openhft.chronicle.wire.ReadMarshallable {
    private final ChronicleQueue matchingToGwWal = Storage.self().matchingToGwWal();

    private final Aeron aeron;
    private Publication publication;
    private Subscription controlSubscription;
    private ExcerptTailer tailer;
    private final BufferClaim bufferClaim = new BufferClaim();
    private final UnsafeBuffer payloadWrapBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    private AeronState currentState = AeronState.WAITING;

    // 暫存發送上下文，消除 Lambda 捕獲
    private int ctxMsgType;
    private long ctxMatchingSeq;

    @PostConstruct public void init() { start("core-result-sender"); }

    @Override
    protected void onStart() {
        publication = aeron.addPublication(AeronChannel.GATEWAY_URL, AeronChannel.DATA_STREAM_ID);
        controlSubscription = aeron.addSubscription(AeronChannel.MATCHING_URL, AeronChannel.CONTROL_STREAM_ID);
        tailer = matchingToGwWal.createTailer();
        currentState = AeronState.WAITING;
        log.info("AeronSender (Core) 啟動成功...");
    }

    private long backPressureCount = 0;

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !publication.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("檢測到 Gateway 斷開，回退至靜默等待模式...");
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
                log.warn("警告：回報鏈路偵測到嚴重背壓，請檢查 Gateway 接收效能！");
                backPressureCount = 0;
            }
        }
        
        return workDone;
    }

    /** 
      指令讀取回調：以統一格式發送回報「信封」
     */
    @Override
    public void readMarshallable(WireIn wire) {
        this.ctxMsgType = wire.read(ChronicleWireKey.msgType).int32();
        long mSeq = wire.read(ChronicleWireKey.matchingSeq).int64();
        this.ctxMatchingSeq = (mSeq == 0) ? tailer.index() : mSeq;
        
        // 提取 Body：統一使用 payload 欄位
        reusableBytes.clear();
        wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
        
        final int payloadLength = (int) reusableBytes.readRemaining();
        
        // 發送：累加背壓重試次數
        this.backPressureCount += AeronUtil.claimAndSend(publication, bufferClaim, 12 + payloadLength, idleStrategy, running, (buffer, offset) -> {
            buffer.putInt(offset, ctxMsgType);
            buffer.putLong(offset + 4, ctxMatchingSeq);
            if (payloadLength > 0) {
                payloadWrapBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), payloadLength);
                buffer.putBytes(offset + 12, payloadWrapBuffer, 0, payloadLength);
            }
        });
    }

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset) == MsgType.RESUME) {
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
        reusableBytes.releaseLast();
    }
}
