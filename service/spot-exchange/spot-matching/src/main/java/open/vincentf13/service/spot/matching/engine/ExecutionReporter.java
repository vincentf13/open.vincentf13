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
 執行回報發送器 (Execution Reporter) - 批量優化版
 職責：收集單次指令處理產生的所有回報，並在最後一次性寫入 WAL
 */
@Component
public class ExecutionReporter {
    private final ChronicleQueue matchingToGwWal = Storage.self().matchingToGwWal();
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    
    /** 批量緩衝區：暫存單筆指令產生的所有回報 */
    private final Bytes<ByteBuffer> batchBytes = Bytes.elasticByteBuffer(4096);
    private final UnsafeBuffer sbeWrapBuffer = new UnsafeBuffer(0, 0);

    @Setter private boolean replaying = false;

    /** 
      將回報寫入批量緩衝區 (暫不落地)
     */
    public void sendReport(long uid, long oid, String cid, OrderStatus s, long lp, long lq, long cq, long ap, long ts) {
        if (replaying) return;
        
        // SBE 編碼至緩衝區末尾
        int pos = (int) batchBytes.writePosition();
        sbeWrapBuffer.wrap(batchBytes.addressForWrite(pos), (int) batchBytes.realCapacity() - pos);
        
        int sbeLen = SbeCodec.encode(sbeWrapBuffer, 0, executionEncoder
                .timestamp(ts).userId(uid).orderId(oid).status(s)
                .lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap)
                .clientOrderId(cid == null ? "" : cid));
        
        batchBytes.writePosition(pos + sbeLen);
    }

    /** 
      將緩衝區內的所有回報一次性寫入 WAL
     */
    public void flushBatch(long gwSeq) {
        if (replaying || batchBytes.writePosition() == 0) {
            batchBytes.clear();
            return;
        }

        matchingToGwWal.acquireAppender().writeDocument(wire -> {
            wire.write(ChronicleWireKey.gwSeq).int64(gwSeq);
            // 寫入包含多個 SBE 封包的二進位塊
            wire.write(ChronicleWireKey.payload).bytes(batchBytes);
        });
        
        batchBytes.clear();
    }

    public void sendAuthSuccess(long userId, long gwSeq) {
        if (replaying) return;
        matchingToGwWal.acquireAppender().writeDocument(w -> {
            w.write(ChronicleWireKey.topic).text("auth.success");
            w.write(ChronicleWireKey.gwSeq).int64(gwSeq);
            w.write(ChronicleWireKey.userId).int64(userId);
        });
    }

    public void resendReport(Order o, long gwSeq) {
        OrderStatus s = switch (o.getStatus()) {
            case 2 -> OrderStatus.FILLED;
            case 3 -> OrderStatus.REJECTED;
            case 1 -> OrderStatus.PARTIALLY_FILLED;
            default -> OrderStatus.NEW;
        };
        sendReport(o.getUserId(), o.getOrderId(), o.getClientOrderId(), s, 0, 0, o.getFilled(), 0, System.currentTimeMillis());
        flushBatch(gwSeq);
    }

    public void close() {
        batchBytes.releaseLast();
    }
}
