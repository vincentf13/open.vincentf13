package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronSender;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.alloc.NativeUnsafeBuffer;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Gateway Aeron 發送器
 職責：讀取客戶端指令流並發送至 Matching Core，實現熱點路徑零物件分配
 */
@Slf4j
@Component
public class AeronSender extends AbstractAeronSender {

    public AeronSender(Aeron aeron) {
        super(aeron, Storage.self().clientToGwWal(), 
              AeronChannel.MATCHING_URL, AeronChannel.DATA_STREAM_ID,
              AeronChannel.GATEWAY_URL, AeronChannel.CONTROL_STREAM_ID);
    }

    @PostConstruct public void init() { start("gw-command-sender"); }

    @Override
    protected void onStart() {
        super.onStart();
        log.info("AeronSender (Gateway) 啟動成功...");
    }

    /** 指令讀取回調：零分配處理 */
    @Override
    public void readMarshallable(WireIn wire) {
        final long ctxSeq = tailer.index();
        final int ctxMsgType = wire.read(ChronicleWireKey.msgType).int32();
        
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
                final NativeUnsafeBuffer scratchBuffer = ThreadContext.get().getScratchBuffer();
                scratchBuffer.clear();
                wire.read(ChronicleWireKey.payload).bytes(scratchBuffer.bytes());
                final int payloadLength = (int) scratchBuffer.bytes().readRemaining();
                
                this.backPressureCount += AeronUtil.claimAndSend(publication, bufferClaim, 12 + payloadLength, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MsgType.ORDER_CREATE);
                    buffer.putLong(offset + 4, ctxSeq);
                    buffer.putBytes(offset + 12, scratchBuffer.wrapForRead(), 0, payloadLength);
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
}
