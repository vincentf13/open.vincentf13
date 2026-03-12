package open.vincentf13.service.spot.gateway.aeron;

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

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Gateway Aeron 發送器 (災難恢復增強版)
 職責：從 GW WAL 讀取指令並發送至核心引擎
 最佳化：進度由接收端 (Core) 透過控制通道決定，本地不再維護持久化位點
 */
@Component
public class AeronSender extends Worker {
    private final Aeron aeron;
    private Publication publication;
    private Subscription controlSubscription;
    private ExcerptTailer tailer;
    private final BufferClaim bufferClaim = new BufferClaim();
    private final UnsafeBuffer payloadWrapBuffer = new UnsafeBuffer(0, 0);

    private AeronState currentState = AeronState.WAITING;

    public AeronSender(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct public void init() { start("gw-command-sender"); }

    @Override
    protected void onStart() {
        publication = aeron.addPublication(AeronChannel.MATCHING_URL, AeronChannel.DATA_STREAM_ID);
        controlSubscription = aeron.addSubscription(AeronChannel.GATEWAY_URL, AeronChannel.CONTROL_STREAM_ID);
        
        // 僅建立 Tailer，不執行跳轉，等待握手訊號
        tailer = Storage.self().gatewayQueue().createTailer();
        currentState = AeronState.WAITING;
        log.info("AeronSender 啟動成功，進入靜默等待狀態...");
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.WAITING) {
            return controlSubscription.poll(resumeHandler, 1);
        }

        if (currentState == AeronState.SENDING) {
            boolean handled = tailer.readDocument(wire -> {
                long seq = tailer.index();
                int msgType = wire.read(ChronicleWireKey.msgType).int32();
                
                if (msgType == MsgType.AUTH) {
                    long userId = wire.read(ChronicleWireKey.userId).int64();
                    AeronUtil.claimAndSend(publication, bufferClaim, 20, idleStrategy, running, (buffer, offset) -> {
                        buffer.putInt(offset, MsgType.AUTH);
                        buffer.putLong(offset + 4, seq);
                        buffer.putLong(offset + 12, userId);
                    });
                } else if (msgType == MsgType.ORDER_CREATE) {
                    wire.read(ChronicleWireKey.payload).bytes(payload -> {
                        int payloadLength = (int) payload.readRemaining();
                        AeronUtil.claimAndSend(publication, bufferClaim, 12 + payloadLength, idleStrategy, running, (buffer, offset) -> {
                            buffer.putInt(offset, MsgType.ORDER_CREATE);
                            buffer.putLong(offset + 4, seq);
                            payloadWrapBuffer.wrap(payload.addressForRead(payload.readPosition()), payloadLength);
                            buffer.putBytes(offset + 12, payloadWrapBuffer, 0, payloadLength);
                        });
                    });
                }
            });
            return handled ? 1 : 0;
        }
        return 0;
    }

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        int msgType = buffer.getInt(offset);
        if (msgType == MsgType.RESUME) {
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
    }
}
