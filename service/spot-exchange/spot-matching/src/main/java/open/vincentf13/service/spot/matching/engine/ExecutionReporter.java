package open.vincentf13.service.spot.matching.engine;

import lombok.Setter;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.alloc.SbeCodec;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.command.*;
import open.vincentf13.service.spot.sbe.OrderStatus;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 執行回報器 (Execution Reporter)
 * 職責：封裝各類成交/指令狀態回報邏輯，實現業務物件到 SBE/WAL 的自動轉換
 */
public class ExecutionReporter {
    private final ChronicleQueue engineSenderWal = Storage.self().engineSenderWal();
    
    @Setter private boolean isReplaying = false;

    /** 
     * 自動回報 Taker 的最終狀態
     * 判定邏輯：New -> reportAccepted, Else -> reportMatched
     */
    public void reportTakerFinalState(Order taker) {
        if (taker.getStatus() == OrderStatus.NEW.ordinal()) {
            reportAccepted(taker);
        } else {
            reportMatched(taker, 0, 0); 
        }
    }

    /** 回報：訂單已接受 (Accepted / New) */
    public void reportAccepted(Order order) {
        if (isReplaying) return;
        int sbeLength = SbeCodec.encodeToScratchAcceptedReport(
                order.getTimestamp(), order.getUserId(), order.getOrderId(), order.getClientOrderId());
        
        writeReport(MsgType.ORDER_ACCEPTED, order.getLastSeq(), sbeLength);
    }

    /** 回報：訂單被拒絕 (Rejected) */
    public void reportRejected(long userId, long clientOrderId, long timestamp, long gatewaySequence) {
        if (isReplaying) return;
        int sbeLength = SbeCodec.encodeToScratchRejectedReport(timestamp, userId, clientOrderId);
        writeReport(MsgType.ORDER_REJECTED, gatewaySequence, sbeLength);
    }

    /** 回報：訂單已撤單 (Canceled) */
    public void reportCanceled(Order order) {
        if (isReplaying) return;
        int sbeLength = SbeCodec.encodeToScratchCanceledReport(
                System.currentTimeMillis(), order.getUserId(), order.getOrderId(), order.getFilled(), order.getClientOrderId());
        
        writeReport(MsgType.ORDER_CANCELED, order.getLastSeq(), sbeLength);
    }

    /** 回報：訂單成交 (Trade / Matched) */
    public void reportMatched(Order order, long lastPrice, long lastQuantity) {
        if (isReplaying) return;
        OrderStatus status = order.getQty() == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        
        int sbeLength = SbeCodec.encodeToScratchMatchedReport(
                order.getTimestamp(), order.getUserId(), order.getOrderId(), status, 
                lastPrice, lastQuantity, order.getFilled(), 0, order.getClientOrderId());
        
        writeReport(MsgType.ORDER_MATCHED, order.getLastSeq(), sbeLength);
    }

    /** 回報：用戶認證結果 */
    public void reportAuth(long userId, long gatewaySequence) {
        if (isReplaying) return;
        AuthReport report = ThreadContext.get().getAuthReport();
        report.setGatewaySeq(gatewaySequence);
        report.setUserId(userId);

        writeWal(MsgType.AUTH_REPORT, report);
    }

    /** 回報：充值成功 */
    public void reportDeposit(long userId, int assetId, long amount, long gatewaySequence) {
        if (isReplaying) return;
        DepositReport report = ThreadContext.get().getDepositReport();
        report.setGatewaySeq(gatewaySequence);
        report.setUserId(userId);
        report.setAssetId(assetId);
        report.setAmount(amount);

        writeWal(MsgType.DEPOSIT_REPORT, report);
    }

    /** 內部工具：將 ScratchBuffer 內容包裝成 Report 並寫入 WAL */
    private void writeReport(int msgType, long gatewaySequence, int sbeLength) {
        ThreadContext context = ThreadContext.get();
        // 這裡利用了 fillFromScratch 的自動封裝
        BytesMarshallable report = switch (msgType) {
            case MsgType.ORDER_ACCEPTED -> context.getOrderAcceptedReport();
            case MsgType.ORDER_REJECTED -> context.getOrderRejectedReport();
            case MsgType.ORDER_CANCELED -> context.getOrderCanceledReport();
            case MsgType.ORDER_MATCHED  -> context.getOrderMatchReport();
            default -> throw new IllegalArgumentException("Unknown msgType: " + msgType);
        };
        
        // 統一設值邏輯，消除重複代碼
        if (report instanceof OrderAcceptedReport r) { r.setGatewaySeq(gatewaySequence); r.fillFromScratch(sbeLength); }
        else if (report instanceof OrderRejectedReport r) { r.setGatewaySeq(gatewaySequence); r.fillFromScratch(sbeLength); }
        else if (report instanceof OrderCanceledReport r) { r.setGatewaySeq(gatewaySequence); r.fillFromScratch(sbeLength); }
        else if (report instanceof OrderMatchReport r) { r.setGatewaySeq(gatewaySequence); r.fillFromScratch(sbeLength); }

        writeWal(msgType, report);
    }

    private void writeWal(int msgType, BytesMarshallable model) {
        try (DocumentContext dc = engineSenderWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(model);
        }
    }

    public void close() {}
}
