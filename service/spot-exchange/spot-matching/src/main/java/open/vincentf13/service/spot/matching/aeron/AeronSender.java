package open.vincentf13.service.spot.matching.aeron;

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
 撮合引擎 Aeron 發送器 (Scenario-Specific Edition)
 */
@Slf4j
@Component
public class AeronSender extends AbstractAeronSender {

    public AeronSender(Aeron aeron) {
        super(aeron, Storage.self().engineSenderWal(), 
              AeronChannel.GATEWAY_URL, AeronChannel.CONTROL_STREAM_ID,
              AeronChannel.MATCHING_URL, AeronChannel.DATA_STREAM_ID);
    }

    @PostConstruct public void init() { start("core-report-sender"); }

    @Override
    public void onWalMessage(WireIn wire) {
        final long ctxSeq = tailer.index();
        final int ctxMsgType = wire.read(ChronicleWireKey.msgType).int32();
        final ThreadContext ctx = ThreadContext.get();

        switch (ctxMsgType) {
            case MsgType.AUTH_REPORT -> {
                AuthReport report = ctx.getAuthReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                this.backPressureCount += aeronClient.send(report.encodedLength(), (buffer, offset) -> {
                    report.write(buffer, offset, ctxSeq).userId(report.getUserId()).timestamp(report.getTimestamp());
                });
            }
            case MsgType.DEPOSIT_REPORT -> {
                DepositReport report = ctx.getDepositReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                this.backPressureCount += aeronClient.send(report.encodedLength(), (buffer, offset) -> {
                    report.write(buffer, offset, ctxSeq).userId(report.getUserId()).assetId(report.getAssetId()).amount(report.getAmount()).timestamp(report.getTimestamp());
                });
            }
            case MsgType.ORDER_ACCEPTED -> {
                OrderAcceptedReport report = ctx.getOrderAcceptedReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                this.backPressureCount += aeronClient.send(report.encodedLength(), (buffer, offset) -> {
                    report.write(buffer, offset, ctxSeq).timestamp(report.getTimestamp()).userId(report.getUserId()).orderId(report.getOrderId()).clientOrderId(report.getClientOrderId());
                });
            }
            case MsgType.ORDER_REJECTED -> {
                OrderRejectedReport report = ctx.getOrderRejectedReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                this.backPressureCount += aeronClient.send(report.encodedLength(), (buffer, offset) -> {
                    report.write(buffer, offset, ctxSeq).timestamp(report.getTimestamp()).userId(report.getUserId()).clientOrderId(report.getClientOrderId());
                });
            }
            case MsgType.ORDER_CANCELED -> {
                OrderCanceledReport report = ctx.getOrderCanceledReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                this.backPressureCount += aeronClient.send(report.encodedLength(), (buffer, offset) -> {
                    report.write(buffer, offset, ctxSeq).timestamp(report.getTimestamp()).userId(report.getUserId()).orderId(report.getOrderId()).filledQty(report.getCumQty()).clientOrderId(report.getClientOrderId());
                });
            }
            case MsgType.ORDER_MATCHED -> {
                OrderMatchReport report = ctx.getOrderMatchReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                this.backPressureCount += aeronClient.send(report.encodedLength(), (buffer, offset) -> {
                    report.write(buffer, offset, ctxSeq).timestamp(report.getTimestamp()).userId(report.getUserId()).orderId(report.getOrderId()).status(report.getStatus()).lastPrice(report.getLastPrice()).lastQty(report.getLastQty()).cumQty(report.getCumQty()).avgPrice(report.getAvgPrice()).clientOrderId(report.getClientOrderId());
                });
            }
        }
    }
}
