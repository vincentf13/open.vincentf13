package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.PointerBytesStore;
import open.vincentf13.service.spot.sbe.ExecutionReportEncoder;
import open.vincentf13.service.spot.sbe.MessageHeaderDecoder;
import open.vincentf13.service.spot.sbe.MessageHeaderEncoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * SBE 指令與回報模型的抽象基類
 * 封裝了實例層級的 SBE 工具與緩衝區，完全脫離對 ThreadLocal 的依賴
 */
@Data
public abstract class AbstractSbeModel implements BytesMarshallable {
    public static final int HEADER_SIZE = 8;
    
    protected long seq;
    protected final PointerBytesStore pointBytesStore = new PointerBytesStore();

    // 每個實例專屬的緩衝區 (空間換時間)
    protected final MutableDirectBuffer selfBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
    
    // 實例化編解碼工具
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ExecutionReportEncoder executionReportEncoder = new ExecutionReportEncoder();
    protected final UnsafeBuffer internalBuffer = new UnsafeBuffer(0, 0);

    public void setGatewaySeq(long val) { this.seq = val; }
    public long getGatewaySeq() { return this.seq; }

    protected void wrapHeader(int tid, int bl, int sid, int ver) {
        headerEncoder.wrap(selfBuffer, 0).templateId(tid).blockLength(bl).schemaId(sid).version(ver);
    }

    protected DirectBuffer wrapStore() {
        internalBuffer.wrap(pointBytesStore.addressForRead(0), (int) pointBytesStore.readRemaining());
        return internalBuffer;
    }

    public void fillFrom(open.vincentf13.service.spot.infra.alloc.aeron.AbstractAeronAlloc<?> aeron) {
        this.seq = aeron.readSeq();
        this.pointBytesStore.set(aeron.getBufferAddress() + aeron.getPayloadOffset(), aeron.getPayloadLength());
    }

    public void fillFrom(DirectBuffer buffer, int offset, int length) {
        this.pointBytesStore.set(buffer.addressOffset() + offset, length);
    }

    protected void encodeReport(long ts, long uid, long oid, OrderStatus st, long lp, long lq, long cq, long ap, long cid) {
        wrapHeader(ExecutionReportEncoder.TEMPLATE_ID, ExecutionReportEncoder.BLOCK_LENGTH, ExecutionReportEncoder.SCHEMA_ID, ExecutionReportEncoder.SCHEMA_VERSION);
        executionReportEncoder.wrap(selfBuffer, HEADER_SIZE)
                .timestamp(ts)
                .userId(uid)
                .orderId(oid)
                .clientOrderId(cid)
                .status(st)
                .lastPrice(lp)
                .lastQty(lq)
                .cumQty(cq)
                .avgPrice(ap);
        
        int totalLength = HEADER_SIZE + executionReportEncoder.encodedLength();
        this.pointBytesStore.set(selfBuffer.addressOffset(), totalLength);
    }

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(seq);
        long len = pointBytesStore.readRemaining();
        bytes.writeStopBit(len);
        if (len > 0) bytes.write(pointBytesStore);
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
