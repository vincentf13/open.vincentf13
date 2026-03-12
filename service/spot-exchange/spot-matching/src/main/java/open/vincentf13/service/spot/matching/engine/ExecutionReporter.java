package open.vincentf13.service.spot.matching.engine;

import lombok.Setter;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
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
 職責：收集單次指令處理產生的回報，並批量寫入回報流 WAL
 */
@Component
public class ExecutionReporter {
    private final ChronicleQueue matchingToGwWal = Storage.self().matchingToGwWal();
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    
    private final Bytes<ByteBuffer> batchBytes = Bytes.elasticByteBuffer(4096);
    private final UnsafeBuffer sbeWrapBuffer = new UnsafeBuffer(0, 0);

    @Setter private boolean replaying = false;

    /** 
      寫入回報至批量緩衝區
     */
    public void sendReport(long uid, long oid, String cid, OrderStatus s, long lp, long lq, long cq, long ap, long ts) {
        if (replaying) return;
        
        int pos = (int) batchBytes.writePosition();
        sbeWrapBuffer.wrap(batchBytes.addressForWrite(pos), (int) batchBytes.realCapacity() - pos);
        
        int sbeLen = SbeCodec.encode(sbeWrapBuffer, 0, executionEncoder
                .timestamp(ts).userId(uid).orderId(oid).status(s)
                .lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap)
                .clientOrderId(cid == null ? "" : cid));
        
        batchBytes.writePosition(pos + sbeLen);
    }

    /** 
      將緩衝區內回報一次性落地
     */
    public void flushBatch(long gwSeq) {
        if (replaying || batchBytes.writePosition() == 0) {
            batchBytes.clear();
            return;
        }

        matchingToGwWal.acquireAppender().writeDocument(wire -> {
            wire.write(ChronicleWireKey.gwSeq).int64(gwSeq);
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

    public void close() {
        batchBytes.releaseLast();
    }
}
