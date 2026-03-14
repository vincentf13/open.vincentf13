package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.command.*;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 核心回報器 (Scenario-Specific Edition)
 */
@Component
@RequiredArgsConstructor
public class ExecutionReporter implements AutoCloseable {
    private final ChronicleQueue engineSenderWal = Storage.self().engineSenderWal();
    private final UnsafeBuffer scratch = new UnsafeBuffer(new byte[256]);
    private boolean isReplaying = false;

    public void setReplaying(boolean replaying) { this.isReplaying = replaying; }

    public void reportAccepted(Order taker) {
        if (isReplaying) return;
        final OrderAcceptedReport report = ThreadContext.get().getOrderAcceptedReport();
        report.write(scratch, 0, taker.getLastSeq())
              .timestamp(System.currentTimeMillis()).userId(taker.getUserId()).orderId(taker.getOrderId()).clientOrderId(taker.getClientOrderId());
        sendReport(MsgType.ORDER_ACCEPTED, report);
    }

    public void reportRejected(long userId, long clientOrderId) {
        if (isReplaying) return;
        final OrderRejectedReport report = ThreadContext.get().getOrderRejectedReport();
        report.write(scratch, 0, MSG_SEQ_NONE)
              .timestamp(System.currentTimeMillis()).userId(userId).clientOrderId(clientOrderId);
        sendReport(MsgType.ORDER_REJECTED, report);
    }

    public void reportCanceled(Order order) {
        if (isReplaying) return;
        final OrderCanceledReport report = ThreadContext.get().getOrderCanceledReport();
        report.write(scratch, 0, order.getLastSeq())
              .timestamp(System.currentTimeMillis()).userId(order.getUserId()).orderId(order.getOrderId()).filledQty(order.getFilled()).clientOrderId(order.getClientOrderId());
        sendReport(MsgType.ORDER_CANCELED, report);
    }

    public void reportTrade(Order order, long price, long qty) {
        if (isReplaying) return;
        final OrderMatchReport report = ThreadContext.get().getOrderMatchReport();
        OrderStatus st = order.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        report.write(scratch, 0, order.getLastSeq())
              .timestamp(System.currentTimeMillis()).userId(order.getUserId()).orderId(order.getOrderId()).status(st).lastPrice(price).lastQty(qty).cumQty(order.getFilled()).avgPrice(price).clientOrderId(order.getClientOrderId());
        sendReport(MsgType.ORDER_MATCHED, report);
    }

    public void reportAuth(long userId) {
        if (isReplaying) return;
        final AuthReport report = ThreadContext.get().getAuthReport();
        report.write(scratch, 0, MSG_SEQ_NONE).userId(userId).timestamp(System.currentTimeMillis());
        sendReport(MsgType.AUTH_REPORT, report);
    }

    public void reportDeposit(long userId, int assetId, long amount) {
        if (isReplaying) return;
        final DepositReport report = ThreadContext.get().getDepositReport();
        report.write(scratch, 0, MSG_SEQ_NONE).userId(userId).assetId(assetId).amount(amount).timestamp(System.currentTimeMillis());
        sendReport(MsgType.DEPOSIT_REPORT, report);
    }

    private void sendReport(int msgType, net.openhft.chronicle.bytes.BytesMarshallable model) {
        try (DocumentContext dc = engineSenderWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(model);
        }
    }

    @Override public void close() {}
}
