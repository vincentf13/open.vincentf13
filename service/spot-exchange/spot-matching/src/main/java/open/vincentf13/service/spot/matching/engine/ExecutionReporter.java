package open.vincentf13.service.spot.matching.engine;

import lombok.Setter;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.command.*;
import open.vincentf13.service.spot.sbe.OrderStatus;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 執行回報器 (Execution Reporter)
 * 職責：封裝業務回報邏輯，將指令執行結果發送到 EngineSenderWal
 */
public class ExecutionReporter {
    private final ChronicleQueue engineSenderWal = Storage.self().engineSenderWal();
    
    @Setter private boolean isReplaying = false;

    /** 訂單已接受 (掛單成功) */
    public void reportAccepted(Order order) {
        if (isReplaying) return;
        OrderAcceptedReport report = ThreadContext.get().getOrderAcceptedReport();
        report.setGatewaySeq(order.getLastSeq());
        report.encode(order.getTimestamp(), order.getUserId(), order.getOrderId(), order.getClientOrderId());
        writeWal(MsgType.ORDER_ACCEPTED, report);
    }

    /** 訂單被拒絕 (例如資產不足) */
    public void reportRejected(long userId, long clientOrderId) {
        if (isReplaying) return;
        ThreadContext context = ThreadContext.get();
        OrderRejectedReport report = context.getOrderRejectedReport();
        report.setGatewaySeq(context.getCurrentGatewaySequence());
        report.encode(System.currentTimeMillis(), userId, clientOrderId);
        writeWal(MsgType.ORDER_REJECTED, report);
    }

    /** 訂單已撤單 */
    public void reportCanceled(Order order) {
        if (isReplaying) return;
        OrderCanceledReport report = ThreadContext.get().getOrderCanceledReport();
        report.setGatewaySeq(order.getLastSeq());
        report.encode(System.currentTimeMillis(), order.getUserId(), order.getOrderId(), order.getFilled(), order.getClientOrderId());
        writeWal(MsgType.ORDER_CANCELED, report);
    }

    /** 訂單成交 (Trade Event) */
    public void reportTrade(Order order, long lastPrice, long lastQuantity) {
        if (isReplaying) return;
        OrderStatus status = order.getFilled() == order.getQty() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        
        OrderMatchReport report = ThreadContext.get().getOrderMatchReport();
        report.setGatewaySeq(order.getLastSeq());
        report.encode(order.getTimestamp(), order.getUserId(), order.getOrderId(), status, 
                lastPrice, lastQuantity, order.getFilled(), 0, order.getClientOrderId());
        
        writeWal(MsgType.ORDER_MATCHED, report);
    }

    /** 用戶認證結果 */
    public void reportAuth(long userId) {
        if (isReplaying) return;
        ThreadContext context = ThreadContext.get();
        AuthReport report = context.getAuthReport();
        report.setGatewaySeq(context.getCurrentGatewaySequence());
        report.setUserId(userId);
        writeWal(MsgType.AUTH_REPORT, report);
    }

    /** 充值成功 */
    public void reportDeposit(long userId, int assetId, long amount) {
        if (isReplaying) return;
        ThreadContext context = ThreadContext.get();
        DepositReport report = context.getDepositReport();
        report.setGatewaySeq(context.getCurrentGatewaySequence());
        report.setUserId(userId);
        report.setAssetId(assetId);
        report.setAmount(amount);
        writeWal(MsgType.DEPOSIT_REPORT, report);
    }

    private void writeWal(int msgType, BytesMarshallable model) {
        try (DocumentContext dc = engineSenderWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(model);
        }
    }

    public void close() {}
}
