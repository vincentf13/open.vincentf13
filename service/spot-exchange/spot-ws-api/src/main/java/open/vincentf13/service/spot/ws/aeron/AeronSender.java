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
                open.vincentf13.service.spot.model.command.AuthCommand cmd = ThreadContext.get().getAuthCommand();
                wire.read(ChronicleWireKey.payload).marshallable(cmd);
                final long userId = cmd.getUserId();
                this.backPressureCount += AeronUtil.claimAndSend(publication, bufferClaim, 20, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MsgType.AUTH);
                    buffer.putLong(offset + 4, ctxSeq);
                    buffer.putLong(offset + 12, userId);
                });
            }
            case MsgType.ORDER_CREATE -> {
                final NativeUnsafeBuffer scratchBuffer = ThreadContext.get().getScratchBuffer();
                scratchBuffer.clear();

                open.vincentf13.service.spot.model.command.OrderCreateCommand cmd = ThreadContext.get().getOrderCreateCommand();
                wire.read(ChronicleWireKey.payload).marshallable(cmd);

                final int payloadLength = (int) cmd.getSbePayload().capacity();
                cmd.getSbePayload().bytesForRead().read(scratchBuffer.buffer().byteArray(), 0, payloadLength);
                
                this.backPressureCount += AeronUtil.claimAndSend(publication, bufferClaim, 12 + payloadLength, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MsgType.ORDER_CREATE);
                    buffer.putLong(offset + 4, ctxSeq);
                    buffer.putBytes(offset + 12, scratchBuffer.wrapForRead(), 0, payloadLength);
                });
            }
            case MsgType.ORDER_CANCEL -> {
                open.vincentf13.service.spot.model.command.OrderCancelCommand cmd = ThreadContext.get().getOrderCancelCommand();
                wire.read(ChronicleWireKey.payload).marshallable(cmd);

                final long uid = cmd.getUserId();
                final long oid = cmd.getOrderId();
                this.backPressureCount += AeronUtil.claimAndSend(publication, bufferClaim, 28, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MsgType.ORDER_CANCEL);
                    buffer.putLong(offset + 4, ctxSeq);
                    buffer.putLong(offset + 12, uid);
                    buffer.putLong(offset + 20, oid);
                });
            }
            case MsgType.DEPOSIT -> {
                open.vincentf13.service.spot.model.command.DepositCommand cmd = ThreadContext.get().getDepositCommand();
                wire.read(ChronicleWireKey.payload).marshallable(cmd);

                final long uid = cmd.getUserId();
                final int aid = cmd.getAssetId();
                final long amt = cmd.getAmount();
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
