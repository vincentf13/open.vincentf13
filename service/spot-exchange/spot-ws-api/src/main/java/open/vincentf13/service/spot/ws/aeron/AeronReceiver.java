package open.vincentf13.service.spot.ws.aeron;

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
 網關 Aeron 接收器 (Unified Model Edition)
 */
@Slf4j
@Component
public class AeronReceiver extends AbstractAeronReceiver {

    public AeronReceiver(Aeron aeron) {
        super(aeron, Storage.self().gatewayReceiverWal(), Storage.self().msgProgressMetadata(),
              MetaDataKey.MsgProgress.GATEWAY_RECEIVE,
              AeronChannel.MATCHING_URL, AeronChannel.DATA_STREAM_ID,
              AeronChannel.GATEWAY_URL, AeronChannel.CONTROL_STREAM_ID);
    }

    @PostConstruct public void init() { start("gw-report-receiver"); }

    @Override
    public void onMessage(DirectBuffer buffer, int offset, int length) {
        final int msgType = buffer.getInt(offset);
        final ThreadContext ctx = ThreadContext.get();
        
        switch (msgType) {
            case MsgType.AUTH_REPORT -> {
                AuthReport report = ctx.getAuthReport();
                report.wrap(buffer, offset, length);
                writeReport(msgType, report);
            }
            case MsgType.DEPOSIT_REPORT -> {
                DepositReport report = ctx.getDepositReport();
                report.wrap(buffer, offset, length);
                writeReport(msgType, report);
            }
            case MsgType.ORDER_ACCEPTED, MsgType.ORDER_REJECTED, MsgType.ORDER_CANCELED, MsgType.ORDER_MATCHED -> {
                OrderMatchReport report = ctx.getOrderMatchReport();
                report.wrap(buffer, offset, length);
                writeReport(msgType, report);
            }
        }
    }

    private void writeReport(int msgType, net.openhft.chronicle.bytes.BytesMarshallable model) {
        try (DocumentContext dc = wal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(model);
        }
    }
}
