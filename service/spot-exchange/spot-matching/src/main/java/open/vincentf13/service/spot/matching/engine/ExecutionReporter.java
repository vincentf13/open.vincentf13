package open.vincentf13.service.spot.matching.engine;

import lombok.Setter;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
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
 職責：收集指令產生的所有回報，並批量、原子地寫入 WAL
 */
@Component
public class ExecutionReporter {
    private final ChronicleQueue matchingToGwWal = Storage.self().matchingToGwWal();
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    
    /** 批量緩衝區：擴大至 64KB，確保即使產生大量成交也能安全容納 */
    private final Bytes<ByteBuffer> batchBytes = Bytes.elasticByteBuffer(65536);
    private final UnsafeBuffer sbeWrapBuffer = new UnsafeBuffer(0, 0);

    @Setter private boolean replaying = false;

    public void sendReport(long uid, long oid, long cid, OrderStatus s, long lp, long lq, long cq, long ap, long ts) {
        if (replaying) return;
        
        int pos = (int) batchBytes.writePosition();
        // 確保緩衝區空間足夠 (預留 256B 給 SBE Header + Body)
        batchBytes.ensureCapacity(pos + 256); 
        
        sbeWrapBuffer.wrap(batchBytes.addressForWrite(pos), (int) (batchBytes.realCapacity() - pos));
        
        int sbeLen = SbeCodec.encode(sbeWrapBuffer, 0, executionEncoder
                .timestamp(ts).userId(uid).orderId(oid).status(s)
                .lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap)
                .clientOrderId(cid));
        
        batchBytes.writePosition(pos + sbeLen);
    }

    public void flushBatch(long gwSeq) {
        if (replaying || batchBytes.writePosition() == 0) {
            batchBytes.clear();
            return;
        }

        try (DocumentContext dc = matchingToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.EXECUTION_REPORT);
            dc.wire().write(ChronicleWireKey.matchingSeq).int64(gwSeq);
            dc.wire().write(ChronicleWireKey.payload).bytes(batchBytes);
        }
        
        batchBytes.clear();
    }

    public void sendAuthSuccess(long userId, long gwSeq) {
        if (replaying) return;
        
        // 複用 batchBytes 臨時寫入 userId
        batchBytes.clear();
        batchBytes.writeLong(userId);
        
        try (DocumentContext dc = matchingToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.AUTH_REPORT);
            dc.wire().write(ChronicleWireKey.matchingSeq).int64(gwSeq);
            dc.wire().write(ChronicleWireKey.payload).bytes(batchBytes);
        }
        
        batchBytes.clear();
    }

    public void close() {
        batchBytes.releaseLast();
    }
}
