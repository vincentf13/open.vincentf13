package open.vincentf13.service.spot.matching.engine;

import lombok.Setter;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.sbe.ExecutionReportEncoder;
import open.vincentf13.service.spot.sbe.OrderStatus;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 執行回報發送器 (Execution Reporter)
 職責：收集並批量執行事務寫入，確保回報的原子性落地
 */
@Component
public class ExecutionReporter {
    private final ChronicleQueue matchingToGwWal = Storage.self().matchingToGwWal();
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    
    private final Bytes<ByteBuffer> batchBytes = Bytes.elasticByteBuffer(4096);
    private final UnsafeBuffer sbeWrapBuffer = new UnsafeBuffer(0, 0);

    @Setter private boolean replaying = false;

    public void sendReport(long uid, long oid, long cid, OrderStatus s, long lp, long lq, long cq, long ap, long ts) {
        if (replaying) return;
        
        int pos = (int) batchBytes.writePosition();
        sbeWrapBuffer.wrap(batchBytes.addressForWrite(pos), (int) batchBytes.realCapacity() - pos);
        
        int sbeLen = SbeCodec.encode(sbeWrapBuffer, 0, executionEncoder
                .timestamp(ts).userId(uid).orderId(oid).status(s)
                .lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap)
                .clientOrderId(cid));
        
        batchBytes.writePosition(pos + sbeLen);
    }

    /** 
      事務批量寫入：使用 DocumentContext 確保 Payload 完整性
     */
    public void flushBatch(long gwSeq) {
        if (replaying || batchBytes.writePosition() == 0) {
            batchBytes.clear();
            return;
        }

        try (DocumentContext dc = matchingToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.gwSeq).int64(gwSeq);
            dc.wire().write(ChronicleWireKey.payload).bytes(batchBytes);
        }
        
        batchBytes.clear();
    }

    public void sendAuthSuccess(long userId, long gwSeq) {
        if (replaying) return;
        try (DocumentContext dc = matchingToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.topic).text("auth.success");
            dc.wire().write(ChronicleWireKey.gwSeq).int64(gwSeq);
            dc.wire().write(ChronicleWireKey.userId).int64(userId);
        }
    }

    public void close() {
        batchBytes.releaseLast();
    }
}
