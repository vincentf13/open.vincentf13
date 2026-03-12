package open.vincentf13.service.spot.matching.engine;

import lombok.Setter;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
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
 職責：將引擎產生的成交回報、認證結果等封裝為 SBE 格式並持久化至回報流 WAL
 */
@Component
public class ExecutionReporter {
    private final ChronicleQueue matchingToGwWal = Storage.self().matchingToGwWal();

    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    private final Bytes<ByteBuffer> outboundBytes = Bytes.elasticByteBuffer(1024);
    private final UnsafeBuffer outboundSbeBuffer = new UnsafeBuffer(0, 0);

    /** 由 Engine 統一控制的重播狀態 */
    @Setter private boolean replaying = false;

    /** 
      發送成交回報 (ExecutionReport)
      若處於 replaying 狀態，則靜默攔截所有發送
     */
    public void sendReport(long uid, long oid, String cid, OrderStatus s, long lp, long lq, long cq, long ap, long ts, long gwSeq) {
        if (replaying) return;
        
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
                .clientOrderId(cid == null ? "" : cid));
        
        outboundBytes.writePosition(sbeLen);
        
        matchingToGwWal.acquireAppender().writeDocument(wire -> {
            wire.write(ChronicleWireKey.msgType).int32(executionEncoder.sbeTemplateId());
            wire.write(ChronicleWireKey.gwSeq).int64(gwSeq);
            wire.write(ChronicleWireKey.payload).bytes(outboundBytes);
        });
    }

    /** 重發訂單回報 */
    public void resendReport(Order o, long gwSeq) {
        OrderStatus s = switch (o.getStatus()) {
            case 2 -> OrderStatus.FILLED;
            case 3 -> OrderStatus.REJECTED;
            case 1 -> OrderStatus.PARTIALLY_FILLED;
            default -> OrderStatus.NEW;
        };
        sendReport(o.getUserId(), o.getOrderId(), o.getClientOrderId(), s, 0, 0, o.getFilled(), 0, System.currentTimeMillis(), gwSeq);
    }

    /** 發送認證成功回報 */
    public void sendAuthSuccess(long userId, long gwSeq) {
        if (replaying) return;
        matchingToGwWal.acquireAppender().writeDocument(w -> {
            w.write(ChronicleWireKey.topic).text("auth.success");
            w.write(ChronicleWireKey.gwSeq).int64(gwSeq);
            w.write(ChronicleWireKey.userId).int64(userId);
        });
    }

    public void close() {
        outboundBytes.releaseLast();
    }
}
