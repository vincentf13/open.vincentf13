package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.command.*;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 Gateway Aeron 接收器
 職責：監聽核心引擎回報並落地 WAL，達成零物件分配
 */
@Slf4j
@Component
public class AeronReceiver extends AbstractAeronReceiver {

    public AeronReceiver(Aeron aeron) {
        super(aeron, Storage.self().matchingToGwWal(), Storage.self().msgMetadata(),
              MetaDataKey.Msg.GW_RECEVIER_POINT, AeronChannel.GATEWAY_URL, AeronChannel.DATA_STREAM_ID,
              AeronChannel.MATCHING_URL, AeronChannel.CONTROL_STREAM_ID);
    }

    @PostConstruct public void init() { start("gw-result-receiver"); }

    @Override
    protected void onStart() {
        super.onStart();
        log.info("AeronReceiver (Gateway) 啟動：當前進度: {}，進入握手狀態...", progress.getLastProcessedSeq());
    }

    @Override
    protected void onMessage(org.agrona.DirectBuffer buffer, int offset, int length) {
        final int msgType = buffer.getInt(offset);
        final long seq = buffer.getLong(offset + 4);
        ThreadContext ctx = ThreadContext.get();

        try (DocumentContext dc = wal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            
            switch (msgType) {
                case MsgType.ORDER_ACCEPTED -> {
                    OrderAcceptedWal model = ctx.getOrderAcceptedWal();
                    model.setMatchingSeq(seq);
                    model.fillFrom(buffer, offset + 12, length - 12);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(model);
                }
                case MsgType.ORDER_REJECTED -> {
                    OrderRejectedWal model = ctx.getOrderRejectedWal();
                    model.setMatchingSeq(seq);
                    model.fillFrom(buffer, offset + 12, length - 12);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(model);
                }
                case MsgType.ORDER_CANCELED -> {
                    OrderCanceledWal model = ctx.getOrderCanceledWal();
                    model.setMatchingSeq(seq);
                    model.fillFrom(buffer, offset + 12, length - 12);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(model);
                }
                case MsgType.ORDER_MATCHED  -> {
                    OrderMatchWal model = ctx.getOrderMatchWal();
                    model.setMatchingSeq(seq);
                    model.fillFrom(buffer, offset + 12, length - 12);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(model);
                }
                case MsgType.AUTH_REPORT -> {
                    AuthReportWal authReport = ctx.getAuthReportWal();
                    authReport.setMatchingSeq(seq);
                    authReport.setUserId(buffer.getLong(offset + 12));
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(authReport);
                }
            }
        }
    }
}
