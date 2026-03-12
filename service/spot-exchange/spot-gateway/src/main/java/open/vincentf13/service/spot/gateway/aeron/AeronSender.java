package open.vincentf13.service.spot.gateway.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.queue.ExcerptTailer;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Gateway Aeron 發送器 (災難恢復增強版)
 職責：從 GW WAL 讀取指令並發送至核心引擎
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

    @PostConstruct public void init() { start("gw-command-sender"); }

    @Override
    protected void onStart() {
        publication = aeron.addPublication(AeronChannel.MATCHING_URL, AeronChannel.DATA_STREAM_ID);
        controlSubscription = aeron.addSubscription(AeronChannel.GATEWAY_URL, AeronChannel.CONTROL_STREAM_ID);
        
        tailer = Storage.self().gatewayQueue().createTailer();
        currentState = AeronState.WAITING;
        log.info("AeronSender 啟動，進入靜默等待狀態...");
    }

    @Override
    protected int doWork() {
        // 1. 活性檢查：若接收端連線中斷，回退至等待模式
        if (currentState == AeronState.SENDING && !publication.isConnected()) {
            currentState = AeronState.WAITING;
            log.warn("檢測到接收端 (Matching Core) 連線中斷，回退至靜默等待模式...");
        }

        int workDone = 0;

        // 2. 握手邏輯：僅在 WAITING 狀態下監聽握手訊號
        if (currentState == AeronState.WAITING) {
            workDone = controlSubscription.poll(resumeHandler, 1);
        }

        // 3. 發送邏輯：僅在 SENDING 狀態下執行數據發送
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
                
                progress.setLastProcessedSeq(seq);
                Storage.self().metadata().put(MetaDataKey.PK_GW_COMMAND_SENDER, progress);
            });
            if (handled) workDone++;
        }
        
        return workDone;
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
