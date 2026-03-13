package open.vincentf13.service.spot.ws.aeron;

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
 Gateway Aeron 發送器
 職責：讀取客戶端指令流並發送至 Matching Core，實現熱點路徑零物件分配
 */
@Slf4j
@Component
@RequiredArgsConstructor
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
    private long backPressureCount = 0;

    // 執行上下文：消除 Lambda 捕獲
    private int ctxMsgType;
    private long ctxSeq;

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
            // 批量發送優化：一次工作周期處理最多 100 條指令
            for (int i = 0; i < 100; i++) {
                if (tailer.readDocument(this)) {
                    workDone++;
                } else {
                    break;
                }
            }
            
            if (workDone > 0 && backPressureCount > 1000) {
                log.warn("警告：指令鏈路偵測到嚴重背壓，核心引擎可能處理過慢或 GC 中！");
                backPressureCount = 0;
            }
        }
        return workDone;
    }

    /** 指令讀取回調：零分配處理 */
    @Override
    public void readMarshallable(WireIn wire) {
        this.ctxSeq = tailer.index();
        this.ctxMsgType = wire.read(ChronicleWireKey.msgType).int32();
        
        switch (ctxMsgType) {
            case MsgType.AUTH -> {
                final long userId = wire.read(ChronicleWireKey.userId).int64();
                this.backPressureCount += AeronUtil.claimAndSend(publication, bufferClaim, 20, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MsgType.AUTH);
                    buffer.putLong(offset + 4, ctxSeq);
                    buffer.putLong(offset + 12, userId);
                });
            }
            case MsgType.ORDER_CREATE -> {
                reusableBytes.clear();
                wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
                final int payloadLength = (int) reusableBytes.readRemaining();
                
                this.backPressureCount += AeronUtil.claimAndSend(publication, bufferClaim, 12 + payloadLength, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MsgType.ORDER_CREATE);
                    buffer.putLong(offset + 4, ctxSeq);
                    payloadWrapBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), payloadLength);
                    buffer.putBytes(offset + 12, payloadWrapBuffer, 0, payloadLength);
                });
            }
            case MsgType.ORDER_CANCEL -> {
                final long uid = wire.read(ChronicleWireKey.userId).int64();
                final long oid = wire.read(ChronicleWireKey.data).int64();
                this.backPressureCount += AeronUtil.claimAndSend(publication, bufferClaim, 28, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MsgType.ORDER_CANCEL);
                    buffer.putLong(offset + 4, ctxSeq);
                    buffer.putLong(offset + 12, uid);
                    buffer.putLong(offset + 20, oid);
                });
            }
            case MsgType.DEPOSIT -> {
                final long uid = wire.read(ChronicleWireKey.userId).int64();
                final int aid = wire.read(ChronicleWireKey.assetId).int32();
                final long amt = wire.read(ChronicleWireKey.data).int64();
                this.backPressureCount += AeronUtil.claimAndSend(publication, bufferClaim, 32, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MsgType.DEPOSIT);
                    buffer.putLong(offset + 4, ctxSeq);
                    buffer.putLong(offset + 12, uid);
                    buffer.putInt(offset + 20, aid);
                    buffer.putLong(offset + 24, amt);
                });
            }
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
