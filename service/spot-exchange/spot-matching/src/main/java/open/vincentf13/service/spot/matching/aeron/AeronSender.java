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
                this.backPressureCount += aeronClient.send(report.totalByteLength(), (buffer, offset) -> {
                    report.write(buffer, offset).set(ctxSeq, report.getTimestamp(), report.getUserId());
                });
            }
            case MsgType.DEPOSIT_REPORT -> {
                DepositReport report = ctx.getDepositReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                this.backPressureCount += aeronClient.send(report.totalByteLength(), (buffer, offset) -> {
                    report.write(buffer, offset).set(ctxSeq, report.getTimestamp(), report.getUserId(), report.getAssetId(), report.getAmount());
                });
            }
            case MsgType.ORDER_ACCEPTED -> {
                OrderAcceptedReport report = ctx.getOrderAcceptedReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                this.backPressureCount += aeronClient.send(report.totalByteLength(), (buffer, offset) -> {
                    report.write(buffer, offset).set(ctxSeq, report.getTimestamp(), report.getUserId(), report.getOrderId(), report.getClientOrderId());
                });
            }
            case MsgType.ORDER_REJECTED -> {
                OrderRejectedReport report = ctx.getOrderRejectedReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                this.backPressureCount += aeronClient.send(report.totalByteLength(), (buffer, offset) -> {
                    report.write(buffer, offset).set(ctxSeq, report.getTimestamp(), report.getUserId(), report.getClientOrderId());
                });
            }
            case MsgType.ORDER_CANCELED -> {
                OrderCanceledReport report = ctx.getOrderCanceledReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                this.backPressureCount += aeronClient.send(report.totalByteLength(), (buffer, offset) -> {
                    report.write(buffer, offset).set(ctxSeq, report.getTimestamp(), report.getUserId(), report.getOrderId(), report.getFilledQty(), report.getClientOrderId());
                });
            }
            case MsgType.ORDER_MATCHED -> {
                OrderMatchReport report = ctx.getOrderMatchReport();
                wire.read(ChronicleWireKey.payload).bytes(report);
                this.backPressureCount += aeronClient.send(report.totalByteLength(), (buffer, offset) -> {
                    report.write(buffer, offset).set(ctxSeq, report.getTimestamp(), report.getUserId(), report.getOrderId(), report.getStatus(), report.getLastPrice(), report.getLastQty(), report.getCumQty(), report.getAvgPrice(), report.getClientOrderId());
                });
            }
        }
    }
}
