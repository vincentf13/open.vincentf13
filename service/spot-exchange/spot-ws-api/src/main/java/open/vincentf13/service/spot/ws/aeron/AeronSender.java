package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.wire.WireIn;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronSender;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.command.*;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;
import static org.agrona.UnsafeAccess.UNSAFE;

/** 
 網關 Aeron 發送器 (Raw WAL 讀取版)
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
        final Bytes<?> bytes = wire.bytes();
        if (bytes.readRemaining() < (long) AbstractSbeModel.BODY_OFFSET) return;

        final long addr = bytes.addressForRead(bytes.readPosition());
        final int msgType = UNSAFE.getInt(addr); // 正確讀取 MsgType
        
        final ThreadContext ctx = ThreadContext.get();
        
        AbstractSbeModel cmd = switch (msgType) {
            case MsgType.AUTH         -> ctx.getAuthCommand();
            case MsgType.ORDER_CREATE  -> ctx.getOrderCreateCommand();
            case MsgType.ORDER_CANCEL  -> ctx.getOrderCancelCommand();
            case MsgType.DEPOSIT      -> ctx.getDepositCommand();
            default -> null;
        };

        if (cmd != null) {
            final int payloadLen = cmd.totalByteLength();
            this.backPressureCount += aeronClient.send(payloadLen, (buffer, offset) -> {
                UNSAFE.copyMemory(addr, buffer.addressOffset() + offset, (long) payloadLen);
                buffer.putLong(offset + AbstractSbeModel.SEQ_OFFSET, ctxSeq);
            });
            bytes.readSkip((long) payloadLen);
        }
    }
}
