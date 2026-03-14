package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.PointerBytesStore;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import org.agrona.DirectBuffer;

/**
 * SBE 指令與回報模型的抽象基類
 * 封裝了 Sequence 管理與 PointerBytesStore 的二進制序列化邏輯
 */
@Data
public abstract class AbstractSbeModel implements BytesMarshallable {
    public static final int HEADER_SIZE = 8;
    
    protected long seq;
    protected final PointerBytesStore pointBytesStore = new PointerBytesStore();

    public void setGatewaySeq(long val) { this.seq = val; }
    public long getGatewaySeq() { return this.seq; }

    protected static void wrapHeader(org.agrona.MutableDirectBuffer buffer, int tid, int bl, int sid, int ver) {
        ThreadContext.get().getHeaderEncoder().wrap(buffer, 0).templateId(tid).blockLength(bl).schemaId(sid).version(ver);
    }

    protected static org.agrona.DirectBuffer wrapStore(PointerBytesStore store) {
        return ThreadContext.get().getScratchBuffer().wrap(store.addressForRead(0), (int) store.readRemaining());
    }

    protected void encodeReport(long ts, long uid, long oid, open.vincentf13.service.spot.sbe.OrderStatus st, long lp, long lq, long cq, long ap, long cid) {
        ThreadContext ctx = ThreadContext.get();
        org.agrona.MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        open.vincentf13.service.spot.sbe.ExecutionReportEncoder encoder = ctx.getExecutionReportEncoder();
        wrapHeader(buffer, open.vincentf13.service.spot.sbe.ExecutionReportEncoder.TEMPLATE_ID, open.vincentf13.service.spot.sbe.ExecutionReportEncoder.BLOCK_LENGTH, open.vincentf13.service.spot.sbe.ExecutionReportEncoder.SCHEMA_ID, open.vincentf13.service.spot.sbe.ExecutionReportEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(ts).userId(uid).orderId(oid).status(st).lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap).clientOrderId(cid);
        fillFromScratch(HEADER_SIZE + encoder.encodedLength());
    }

    public void fillFromScratch(int length) {
        fillFrom(ThreadContext.get().getScratchBuffer().buffer(), 0, length);
    }

    public void fillFrom(open.vincentf13.service.spot.infra.alloc.aeron.AbstractAeronAlloc<?> aeron) {
        this.seq = aeron.readSeq();
        this.pointBytesStore.set(aeron.getBufferAddress() + aeron.getPayloadOffset(), aeron.getPayloadLength());
    }

    public void fillFrom(DirectBuffer buffer, int offset, int length) {
        this.pointBytesStore.set(buffer.addressOffset() + offset, length);
    }

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(seq);
        long len = pointBytesStore.readRemaining();
        bytes.writeStopBit(len);
        if (len > 0) {
            bytes.write(pointBytesStore);
        }
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        seq = bytes.readLong();
        int len = (int) bytes.readStopBit();
        if (len > 0) {
            long address = bytes.addressForRead(bytes.readPosition());
            pointBytesStore.set(address, len);
            bytes.readSkip(len);
        } else {
            pointBytesStore.set(0, 0);
        }
    }
}
