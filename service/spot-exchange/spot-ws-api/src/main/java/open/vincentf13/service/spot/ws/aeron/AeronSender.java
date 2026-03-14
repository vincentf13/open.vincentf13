package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronSender;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.command.*;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Gateway Aeron 發送器 (Unified Model Edition)
 */
@Slf4j
@Component
public class AeronSender extends AbstractAeronSender {

    public AeronSender(Aeron aeron) {
        super(aeron, Storage.self().gatewaySenderWal(), 
              AeronChannel.MATCHING_URL, AeronChannel.DATA_STREAM_ID,
              AeronChannel.GATEWAY_URL, AeronChannel.CONTROL_STREAM_ID);
    }

    @PostConstruct public void init() { start("gw-command-sender"); }

    @Override
    public void onWalMessage(WireIn wire) {
        final long ctxSeq = tailer.index();
        final int ctxMsgType = wire.read(ChronicleWireKey.msgType).int32();
        final ThreadContext ctx = ThreadContext.get();
        
        switch (ctxMsgType) {
            case MsgType.AUTH -> {
                AuthCommand cmd = ctx.getAuthCommand();
                wire.read(ChronicleWireKey.payload).bytes(cmd);
                this.backPressureCount += aeronClient.send(cmd.encodedLength(), (buffer, offset) -> {
                    cmd.write(buffer, offset).set(ctxSeq, cmd.getTimestamp(), cmd.getUserId());
                });
            }
            case MsgType.ORDER_CREATE -> {
                OrderCreateCommand cmd = ctx.getOrderCreateCommand();
                wire.read(ChronicleWireKey.payload).bytes(cmd);
                this.backPressureCount += aeronClient.send(cmd.encodedLength(), (buffer, offset) -> {
                    cmd.write(buffer, offset).set(ctxSeq, cmd.getTimestamp(), cmd.getUserId(), cmd.getSymbolId(), cmd.getPrice(), cmd.getQty(), cmd.getSide(), cmd.getClientOrderId());
                });
            }
            case MsgType.ORDER_CANCEL -> {
                OrderCancelCommand cmd = ctx.getOrderCancelCommand();
                wire.read(ChronicleWireKey.payload).bytes(cmd);
                this.backPressureCount += aeronClient.send(cmd.encodedLength(), (buffer, offset) -> {
                    cmd.write(buffer, offset).set(ctxSeq, cmd.getTimestamp(), cmd.getUserId(), cmd.getOrderId());       
                });
            }
            case MsgType.DEPOSIT -> {
                DepositCommand cmd = ctx.getDepositCommand();
                wire.read(ChronicleWireKey.payload).bytes(cmd);
                this.backPressureCount += aeronClient.send(cmd.encodedLength(), (buffer, offset) -> {
                    cmd.write(buffer, offset).set(ctxSeq, cmd.getTimestamp(), cmd.getUserId(), cmd.getAssetId(), cmd.getAmount());
                });
            }
        }
    }
}
