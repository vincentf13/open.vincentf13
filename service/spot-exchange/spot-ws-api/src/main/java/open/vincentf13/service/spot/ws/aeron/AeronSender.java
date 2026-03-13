package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronSender;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.alloc.SbeCodec;
import open.vincentf13.service.spot.model.command.AuthCommand;
import open.vincentf13.service.spot.model.command.DepositCommand;
import open.vincentf13.service.spot.model.command.OrderCancelCommand;
import open.vincentf13.service.spot.model.command.OrderCreateCommand;
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
        final ThreadContext ctx = ThreadContext.get();
        
        switch (ctxMsgType) {
            case MsgType.AUTH -> {
                AuthCommand cmd = ctx.getAuthCommand();
                wire.read(ChronicleWireKey.payload).bytes(cmd);
                
                // 使用 SBE Decoder 解析
                final long userId = SbeCodec.decodeAuth(cmd.getSbePayload()).userId();
                
                this.backPressureCount += AeronUtil.claimAndSend(publication, bufferClaim, 20, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MsgType.AUTH);
                    buffer.putLong(offset + 4, ctxSeq);
                    buffer.putLong(offset + 12, userId);
                });
            }
            case MsgType.ORDER_CREATE -> {
                OrderCreateCommand cmd = ctx.getOrderCreateCommand();
                wire.read(ChronicleWireKey.payload).bytes(cmd);
                final int payloadLength = (int) cmd.getSbePayload().readRemaining();
                
                this.backPressureCount += AeronUtil.claimAndSend(publication, bufferClaim, 12 + payloadLength, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MsgType.ORDER_CREATE);
                    buffer.putLong(offset + 4, ctxSeq);
                    buffer.putBytes(offset + 12, cmd.getSbePayload().bytesForRead(), 0, payloadLength);
                });
            }
            case MsgType.ORDER_CANCEL -> {
                OrderCancelCommand cmd = ctx.getOrderCancelCommand();
                wire.read(ChronicleWireKey.payload).bytes(cmd);
                
                var decoder = SbeCodec.decodeOrderCancel(cmd.getSbePayload());
                final long userId = decoder.userId();
                final long orderId = decoder.orderId();
                
                this.backPressureCount += AeronUtil.claimAndSend(publication, bufferClaim, 28, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MsgType.ORDER_CANCEL);
                    buffer.putLong(offset + 4, ctxSeq);
                    buffer.putLong(offset + 12, userId);
                    buffer.putLong(offset + 20, orderId);
                });
            }
            case MsgType.DEPOSIT -> {
                DepositCommand cmd = ctx.getDepositCommand();
                wire.read(ChronicleWireKey.payload).bytes(cmd);
                
                var decoder = SbeCodec.decodeDeposit(cmd.getSbePayload());
                final long userId = decoder.userId();
                final int assetId = decoder.assetId();
                final long amount = decoder.amount();
                
                this.backPressureCount += AeronUtil.claimAndSend(publication, bufferClaim, 32, idleStrategy, running, (buffer, offset) -> {
                    buffer.putInt(offset, MsgType.DEPOSIT);
                    buffer.putLong(offset + 4, ctxSeq);
                    buffer.putLong(offset + 12, userId);
                    buffer.putInt(offset + 20, assetId);
                    buffer.putLong(offset + 24, amount);
                });
            }
        }
    }
}
