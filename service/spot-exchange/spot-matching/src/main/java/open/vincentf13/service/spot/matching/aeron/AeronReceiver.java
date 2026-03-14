package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.command.*;
import org.agrona.DirectBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 撮合引擎 Aeron 接收器 (Unified Model Edition)
 */
@Slf4j
@Component
public class AeronReceiver extends AbstractAeronReceiver {

    public AeronReceiver(Aeron aeron) {
        super(aeron, Storage.self().engineReceiverWal(), Storage.self().msgProgressMetadata(),
              MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE,
              AeronChannel.GATEWAY_URL, AeronChannel.DATA_STREAM_ID,
              AeronChannel.MATCHING_URL, AeronChannel.CONTROL_STREAM_ID);
    }

    @PostConstruct public void init() { start("core-command-receiver"); }

    @Override
    public void onMessage(DirectBuffer buffer, int offset, int length) {
        final int msgType = buffer.getInt(offset);
        final ThreadContext ctx = ThreadContext.get();
        AbstractSbeModel model = switch (msgType) {
            case MsgType.AUTH         -> ctx.getAuthCommand();
            case MsgType.ORDER_CREATE  -> ctx.getOrderCreateCommand();
            case MsgType.ORDER_CANCEL  -> ctx.getOrderCancelCommand();
            case MsgType.DEPOSIT      -> ctx.getDepositCommand();
            default -> null;
        };

        if (model != null) {
            model.wrap(buffer, offset, length);
            try (DocumentContext dc = wal.acquireAppender().writingDocument()) {
                dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
                dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(model);
            }
        }
    }
}
