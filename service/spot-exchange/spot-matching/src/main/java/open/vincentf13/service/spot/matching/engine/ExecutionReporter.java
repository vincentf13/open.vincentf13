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
    private final ChronicleQueue matchingToGwWal = Storage.self().matchingToGwWal();
    
    @Setter private boolean isReplaying = false;

    /** 回報：訂單已接受 (Accepted / New) */
    public void reportAccepted(long userId, long orderId, long clientOrderId, long timestamp, long gatewaySequence) {
        if (isReplaying) return;
        ThreadContext context = ThreadContext.get();
        int sbeLength = SbeCodec.encodeAcceptedReport(timestamp, userId, orderId, clientOrderId);
        
        OrderAcceptedWal wal = context.getOrderAcceptedWal();
        wal.setMatchingSeq(gatewaySequence);
        wal.fillFrom(context.getScratchBuffer().buffer(), 0, sbeLength);
        
        writeWal(MsgType.ORDER_ACCEPTED, wal);
    }

    /** 回報：訂單被拒絕 (Rejected) */
    public void reportRejected(long userId, long clientOrderId, long timestamp, long gatewaySequence) {
        if (isReplaying) return;
        ThreadContext context = ThreadContext.get();
        int sbeLength = SbeCodec.encodeRejectedReport(timestamp, userId, clientOrderId);
        
        OrderRejectedWal wal = context.getOrderRejectedWal();
        wal.setMatchingSeq(gatewaySequence);
        wal.fillFrom(context.getScratchBuffer().buffer(), 0, sbeLength);
        
        writeWal(MsgType.ORDER_REJECTED, wal);
    }

    /** 回報：訂單已撤單 (Canceled) */
    public void reportCanceled(long userId, long orderId, long clientOrderId, long filledQuantity, long timestamp, long gatewaySequence) {
        if (isReplaying) return;
        ThreadContext context = ThreadContext.get();
        int sbeLength = SbeCodec.encodeCanceledReport(timestamp, userId, orderId, filledQuantity, clientOrderId);
        
        OrderCanceledWal wal = context.getOrderCanceledWal();
        wal.setMatchingSeq(gatewaySequence);
        wal.fillFrom(context.getScratchBuffer().buffer(), 0, sbeLength);
        
        writeWal(MsgType.ORDER_CANCELED, wal);
    }

    /** 回報：訂單成交 (Trade / Matched) */
    public void reportMatched(long userId, long orderId, long clientOrderId, OrderStatus orderStatus, 
                              long lastPrice, long lastQuantity, long cumulativeQuantity, long averagePrice, 
                              long timestamp, long gatewaySequence) {
        if (isReplaying) return;
        ThreadContext context = ThreadContext.get();
        int sbeLength = SbeCodec.encodeMatchedReport(timestamp, userId, orderId, orderStatus, 
                lastPrice, lastQuantity, cumulativeQuantity, averagePrice, clientOrderId);
        
        OrderMatchWal wal = context.getOrderMatchWal();
        wal.setMatchingSeq(gatewaySequence);
        wal.fillFrom(context.getScratchBuffer().buffer(), 0, sbeLength);
        
        writeWal(MsgType.ORDER_MATCHED, wal);
    }

    /** 回報：用戶認證結果 */
    public void reportAuth(long userId, long matchingSequence) {
        if (isReplaying) return;
        AuthReportWal wal = ThreadContext.get().getAuthReportWal();
        wal.setMatchingSeq(matchingSequence);
        wal.setUserId(userId);

        writeWal(MsgType.AUTH_REPORT, wal);
    }

    private void writeWal(int msgType, BytesMarshallable model) {
        try (DocumentContext dc = matchingToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(model);
        }
    }

    public void close() {
        // 資源由 ThreadContext 統一管理
    }
}
