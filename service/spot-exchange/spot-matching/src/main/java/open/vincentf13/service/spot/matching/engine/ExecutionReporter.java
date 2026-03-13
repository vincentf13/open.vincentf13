package open.vincentf13.service.spot.matching.engine;

import lombok.Setter;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import net.openhft.chronicle.wire.Marshallable;
import open.vincentf13.service.spot.infra.alloc.NativeUnsafeBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.command.*;
import open.vincentf13.service.spot.sbe.ExecutionReportEncoder;
import open.vincentf13.service.spot.sbe.OrderStatus;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 執行回報發送器 (Execution Reporter)
 職責：將撮合結果（成交、撤單、認證成功等）即時寫入回報 WAL
 */
@Component
public class ExecutionReporter {
    private final ChronicleQueue matchingToGwWal = Storage.self().matchingToGwWal();
    
    @Setter private boolean replaying = false;

    /**
     * 發送單筆成交回報
     */
    public void sendReport(long uid, long oid, long cid, OrderStatus s, long lp, long lq, long cq, long ap, long ts, long matchingSeq) {
        if (replaying) return;
        
        ThreadContext ctx = ThreadContext.get();
        NativeUnsafeBuffer scratchBuffer = ctx.getScratchBuffer();
        scratchBuffer.clear();
        
        // 1. SBE 編碼回報主體
        int sbeLen = SbeCodec.encode(scratchBuffer.wrapForWrite(), 0, ctx.getExecutionReportEncoder()
                .timestamp(ts).userId(uid).orderId(oid).status(s)
                .lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap)
                .clientOrderId(cid));
        scratchBuffer.bytes().writePosition(sbeLen);

        // 2. 根據狀態決定場景化模型與 MsgType
        int msgType;
        switch (s) {
            case NEW -> {
                msgType = MsgType.ORDER_ACCEPTED;
                OrderAcceptedWal model = ctx.getOrderAcceptedWal();
                model.fillFrom(scratchBuffer.buffer(), 0, sbeLen, matchingSeq);
                writeWal(msgType, model);
            }
            case REJECTED -> {
                msgType = MsgType.ORDER_REJECTED;
                OrderRejectedWal model = ctx.getOrderRejectedWal();
                model.fillFrom(scratchBuffer.buffer(), 0, sbeLen, matchingSeq);
                writeWal(msgType, model);
            }
            case CANCELED -> {
                msgType = MsgType.ORDER_CANCELED;
                OrderCanceledWal model = ctx.getOrderCanceledWal();
                model.fillFrom(scratchBuffer.buffer(), 0, sbeLen, matchingSeq);
                writeWal(msgType, model);
            }
            default -> {
                msgType = MsgType.ORDER_MATCHED;
                OrderMatchWal model = ctx.getOrderMatchWal();
                model.fillFrom(scratchBuffer.buffer(), 0, sbeLen, matchingSeq);
                writeWal(msgType, model);
            }
        }
    }

    private void writeWal(int msgType, Marshallable model) {
        try (DocumentContext dc = matchingToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            dc.wire().write(ChronicleWireKey.payload).marshallable(model);
        }
    }

    /**
     * 認證成功回報
     */
    public void sendAuthSuccess(long userId, long matchingSeq) {
        if (replaying) return;
        
        ThreadContext ctx = ThreadContext.get();
        AuthReportWal walModel = ctx.getAuthReportWal();
        walModel.setMatchingSeq(matchingSeq);
        walModel.setUserId(userId);

        try (DocumentContext dc = matchingToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.AUTH_REPORT);
            dc.wire().write(ChronicleWireKey.payload).marshallable(walModel);
        }
    }

    public void close() {
        // 現在資源由 ThreadContext 統一管理，此處僅保留簽章
    }
}
