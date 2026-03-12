package open.vincentf13.service.spot.matching.engine;

import net.openhft.chronicle.bytes.Bytes;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.sbe.ExecutionReportEncoder;
import open.vincentf13.service.spot.sbe.OrderStatus;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 執行回報發送器 (Execution Reporter)
 職責：將引擎產生的成交回報、認證結果等封裝為 SBE 或 Wire 格式並持久化至 Result WAL
 */
@Component
public class ExecutionReporter {
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    private final Bytes<ByteBuffer> outboundBytes = Bytes.elasticByteBuffer(1024);
    private final UnsafeBuffer outboundSbeBuffer = new UnsafeBuffer(0, 0);

    /** 
      發送成交回報 (ExecutionReport)
      @param isReplaying 若處於重播模式，則僅執行邏輯不發送外部訊號
     */
    public void sendReport(long uid, long oid, String cid, OrderStatus s, long lp, long lq, long cq, long ap, long ts, long gwSeq, boolean isReplaying) {
        if (isReplaying) return;
        
        outboundBytes.clear();
        outboundSbeBuffer.wrap(outboundBytes.addressForWrite(0), (int)outboundBytes.realCapacity());
        
        int sbeLen = SbeCodec.encode(outboundSbeBuffer, 0, executionEncoder
                .timestamp(ts)
                .userId(uid)
                .orderId(oid)
                .status(s)
                .lastPrice(lp)
                .lastQty(lq)
                .cumQty(cq)
                .avgPrice(ap)
                .clientOrderId(cid));
        
        outboundBytes.writePosition(sbeLen);
        
        Storage.self().resultQueue().acquireAppender().writeDocument(wire -> {
            wire.write(ChronicleWireKey.msgType).int32(executionEncoder.sbeTemplateId());
            wire.write(ChronicleWireKey.gwSeq).int64(gwSeq);
            wire.write(ChronicleWireKey.payload).bytes(outboundBytes);
        });
    }

    /** 
      重發已知訂單的回報
     */
    public void resendReport(Order o, long gwSeq) {
        OrderStatus s = (o.getStatus() == 2) ? OrderStatus.FILLED : 
                        (o.getStatus() == 3) ? OrderStatus.REJECTED : 
                        (o.getStatus() == 1) ? OrderStatus.PARTIALLY_FILLED : OrderStatus.NEW;
        sendReport(o.getUserId(), o.getOrderId(), o.getClientOrderId(), s, 0, 0, o.getFilled(), 0, System.currentTimeMillis(), gwSeq, false);
    }

    /** 
      發送認證成功回報
     */
    public void sendAuthSuccess(long userId, long gwSeq, boolean isReplaying) {
        if (isReplaying) return;
        Storage.self().resultQueue().acquireAppender().writeDocument(w -> {
            w.write(ChronicleWireKey.topic).text("auth.success");
            w.write(ChronicleWireKey.gwSeq).int64(gwSeq);
            w.write(ChronicleWireKey.userId).int64(userId);
        });
    }

    /** 資源釋放 */
    public void close() {
        outboundBytes.releaseLast();
    }
}
