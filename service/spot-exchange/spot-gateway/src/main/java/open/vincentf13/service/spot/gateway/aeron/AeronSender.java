package open.vincentf13.service.spot.gateway.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
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
import java.util.function.Consumer;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Gateway Aeron 發送器
 職責：讀取客戶端指令流並發送至 Matching Core，實現熱點路徑零物件分配
 */
@Component
public class AeronSender extends Worker implements net.openhft.chronicle.wire.ReadMarshallable {
    private final ChronicleQueue clientToGwWal = Storage.self().clientToGwWal();

    private final Aeron aeron;
    private Publication publication;
    private Subscription controlSubscription;
    private ExcerptTailer tailer;
    private final BufferClaim bufferClaim = new BufferClaim();
    private final UnsafeBuffer payloadWrapBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    private AeronState currentState = AeronState.WAITING;

    // 執行上下文：消除 Lambda 捕獲
    private int ctxMsgType;
    private long ctxSeq;

    public AeronSender(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct public void init() { start("gw-command-sender"); }

    @Override
    protected void onStart() {
        publication = aeron.addPublication(AeronChannel.MATCHING_URL, AeronChannel.DATA_STREAM_ID);
        controlSubscription = aeron.addSubscription(AeronChannel.GATEWAY_URL, AeronChannel.CONTROL_STREAM_ID);
        tailer = clientToGwWal.createTailer();
        currentState = AeronState.WAITING;
        log.info("AeronSender (Gateway) 啟動成功...");
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !publication.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("檢測到核心引擎斷開，回退至靜默等待模式...");
        }

        int workDone = 0;
        if (currentState == AeronState.WAITING) {
            workDone = controlSubscription.poll(resumeHandler, 1);
        }

        if (currentState == AeronState.SENDING) {
            if (tailer.readDocument(this)) workDone++;
        }
        return workDone;
    }

    /** 指令讀取回調：零分配處理 */
    @Override
    public void readMarshallable(WireIn wire) {
        this.ctxSeq = tailer.index();
        this.ctxMsgType = wire.read(ChronicleWireKey.msgType).int32();
        
        if (ctxMsgType == MsgType.AUTH) {
            final long userId = wire.read(ChronicleWireKey.userId).int64();
            AeronUtil.claimAndSend(publication, bufferClaim, 20, idleStrategy, running, (buffer, offset) -> {
                buffer.putInt(offset, MsgType.AUTH);
                buffer.putLong(offset + 4, ctxSeq);
                buffer.putLong(offset + 12, userId);
            });
        } else if (ctxMsgType == MsgType.ORDER_CREATE) {
            reusableBytes.clear();
            wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
            final int payloadLength = (int) reusableBytes.readRemaining();
            
            AeronUtil.claimAndSend(publication, bufferClaim, 12 + payloadLength, idleStrategy, running, (buffer, offset) -> {
                buffer.putInt(offset, MsgType.ORDER_CREATE);
                buffer.putLong(offset + 4, ctxSeq);
                payloadWrapBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), payloadLength);
                buffer.putBytes(offset + 12, payloadWrapBuffer, 0, payloadLength);
            });
        }
    }

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset) == MsgType.RESUME) {
            long lastProcessedIndex = buffer.getLong(offset + 4);
            log.info("收到 Core 握手訊號，執行跳轉至: {}", lastProcessedIndex);
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
