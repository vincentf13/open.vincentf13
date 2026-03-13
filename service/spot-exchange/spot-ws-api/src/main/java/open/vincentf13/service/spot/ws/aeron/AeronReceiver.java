package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver;
import open.vincentf13.service.spot.infra.alloc.aeron.AeronEnvelope;
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
        super(aeron, Storage.self().gatewayReceiverWal(), Storage.self().msgMetadata(),
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
        final ThreadContext ctx = ThreadContext.get();
        final AeronEnvelope aeron = ctx.getAeronEnvelope().wrap(buffer, offset, length);
        final int msgType = aeron.readMsgType();

        try (DocumentContext dc = wal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            
            switch (msgType) {
                case MsgType.ORDER_ACCEPTED -> {
                    OrderAcceptedReport report = ctx.getOrderAcceptedReport();
                    report.fillFrom(aeron);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(report);
                }
                case MsgType.ORDER_REJECTED -> {
                    OrderRejectedReport report = ctx.getOrderRejectedReport();
                    report.fillFrom(aeron);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(report);
                }
                case MsgType.ORDER_CANCELED -> {
                    OrderCanceledReport report = ctx.getOrderCanceledReport();
                    report.fillFrom(aeron);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(report);
                }
                case MsgType.ORDER_MATCHED  -> {
                    OrderMatchReport report = ctx.getOrderMatchReport();
                    report.fillFrom(aeron);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(report);
                }
                case MsgType.AUTH_REPORT -> {
                    AuthReport report = ctx.getAuthReport();
                    report.fillFrom(aeron);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(report);
                }
                case MsgType.DEPOSIT_REPORT -> {
                    DepositReport report = ctx.getDepositReport();
                    report.fillFrom(aeron);
                    dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(report);
                }
                }
        }
    }
}
