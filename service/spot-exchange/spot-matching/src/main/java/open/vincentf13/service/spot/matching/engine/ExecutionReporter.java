package open.vincentf13.service.spot.matching.engine;

import lombok.Setter;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.alloc.SbeCodec;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.command.*;
import open.vincentf13.service.spot.sbe.OrderStatus;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 執行回報器 (Execution Reporter)
 * 職責：封裝各類成交/指令狀態回報邏輯，寫入回報 WAL
 */
public class ExecutionReporter {
    private final ChronicleQueue engineSenderWal = Storage.self().engineSenderWal();
    
    @Setter private boolean isReplaying = false;

    /** 回報：訂單已接受 (Accepted / New) */
    public void reportAccepted(long userId, long orderId, long clientOrderId, long timestamp, long gatewaySequence) {
        if (isReplaying) return;
        ThreadContext context = ThreadContext.get();
        int sbeLength = SbeCodec.encodeToScratchAcceptedReport(timestamp, userId, orderId, clientOrderId);
        
        OrderAcceptedReport report = context.getOrderAcceptedReport();
        report.setGatewaySeq(gatewaySequence);
        report.fillFromScratch(sbeLength);
        
        writeWal(MsgType.ORDER_ACCEPTED, report);
    }

    /** 回報：訂單被拒絕 (Rejected) */
    public void reportRejected(long userId, long clientOrderId, long timestamp, long gatewaySequence) {
        if (isReplaying) return;
        ThreadContext context = ThreadContext.get();
        int sbeLength = SbeCodec.encodeToScratchRejectedReport(timestamp, userId, clientOrderId);
        
        OrderRejectedReport report = context.getOrderRejectedReport();
        report.setGatewaySeq(gatewaySequence);
        report.fillFromScratch(sbeLength);
        
        writeWal(MsgType.ORDER_REJECTED, report);
    }

    /** 回報：訂單已撤單 (Canceled) */
    public void reportCanceled(long userId, long orderId, long clientOrderId, long filledQuantity, long timestamp, long gatewaySequence) {
        if (isReplaying) return;
        ThreadContext context = ThreadContext.get();
        int sbeLength = SbeCodec.encodeToScratchCanceledReport(timestamp, userId, orderId, filledQuantity, clientOrderId);
        
        OrderCanceledReport report = context.getOrderCanceledReport();
        report.setGatewaySeq(gatewaySequence);
        report.fillFromScratch(sbeLength);
        
        writeWal(MsgType.ORDER_CANCELED, report);
    }

    /** 回報：訂單成交 (Trade / Matched) */
    public void reportMatched(long userId, long orderId, long clientOrderId, OrderStatus orderStatus, 
                              long lastPrice, long lastQuantity, long cumulativeQuantity, long averagePrice, 
                              long timestamp, long gatewaySequence) {
        if (isReplaying) return;
        ThreadContext context = ThreadContext.get();
        int sbeLength = SbeCodec.encodeToScratchMatchedReport(timestamp, userId, orderId, orderStatus, 
                lastPrice, lastQuantity, cumulativeQuantity, averagePrice, clientOrderId);
        
        OrderMatchReport report = context.getOrderMatchReport();
        report.setGatewaySeq(gatewaySequence);
        report.fillFromScratch(sbeLength);
        
        writeWal(MsgType.ORDER_MATCHED, report);
    }

    /** 回報：用戶認證結果 */
    public void reportAuth(long userId, long gatewaySequence) {
        if (isReplaying) return;
        AuthReport report = ThreadContext.get().getAuthReport();
        report.setGatewaySeq(gatewaySequence);
        report.setUserId(userId);

        writeWal(MsgType.AUTH_REPORT, report);
    }

    private void writeWal(int msgType, BytesMarshallable model) {
        try (DocumentContext dc = engineSenderWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(model);
        }
    }

    public void close() {
        // 資源由 ThreadContext 統一管理
    }
}
