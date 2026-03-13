package open.vincentf13.service.spot.matching.engine;

import lombok.Setter;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.alloc.NativeUnsafeBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
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
    
    /** 批量緩衝區：擴大至 64KB，確保即使產生大量成交也能安全容納 */
    private final NativeUnsafeBuffer nativeUnsafeBuffer = new NativeUnsafeBuffer(65536);

    @Setter private boolean replaying = false;

    public void sendReport(long uid, long oid, long cid, OrderStatus s, long lp, long lq, long cq, long ap, long ts) {
        if (replaying) return;
        
        long pos = nativeUnsafeBuffer.bytes().writePosition();
        // 確保緩衝區空間足夠 (預留 256B 給 SBE Header + Body)
        nativeUnsafeBuffer.bytes().ensureCapacity(pos + 256); 
        
        UnsafeBuffer sbeBuffer = nativeUnsafeBuffer.wrap(nativeUnsafeBuffer.bytes().addressForWrite(pos), (int) (nativeUnsafeBuffer.bytes().realCapacity() - pos));
        
        int sbeLen = SbeCodec.encode(sbeBuffer, 0, ThreadContext.get().getExecutionReportEncoder()
                .timestamp(ts).userId(uid).orderId(oid).status(s)
                .lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap)
                .clientOrderId(cid));
        
        nativeUnsafeBuffer.bytes().writePosition(pos + sbeLen);
    }

    public void flushBatch(long gwSeq) {
        if (replaying || nativeUnsafeBuffer.bytes().writePosition() == 0) {
            nativeUnsafeBuffer.clear();
            return;
        }

        try (DocumentContext dc = matchingToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.EXECUTION_REPORT);
            dc.wire().write(ChronicleWireKey.matchingSeq).int64(gwSeq);
            dc.wire().write(ChronicleWireKey.payload).bytes(nativeUnsafeBuffer.bytes());
        }
        
        nativeUnsafeBuffer.clear();
    }

    public void sendAuthSuccess(long userId, long gwSeq) {
        if (replaying) return;
        
        // 複用 NativeUnsafeBuffer 臨時寫入 userId
        nativeUnsafeBuffer.clear();
        nativeUnsafeBuffer.bytes().writeLong(userId);
        
        try (DocumentContext dc = matchingToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.AUTH_REPORT);
            dc.wire().write(ChronicleWireKey.matchingSeq).int64(gwSeq);
            dc.wire().write(ChronicleWireKey.payload).bytes(nativeUnsafeBuffer.bytes());
        }
        
        nativeUnsafeBuffer.clear();
    }

    public void close() {
        nativeUnsafeBuffer.release();
    }
}
