package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.PointerBytesStore;
import open.vincentf13.service.spot.infra.alloc.NativeUnsafeBuffer;
import open.vincentf13.service.spot.sbe.MessageHeaderDecoder;
import open.vincentf13.service.spot.sbe.MessageHeaderEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * SBE 指令與回報模型的抽象基類
 * 封裝了實例層級的 SBE 工具與二進制序列化邏輯，擺脫對 ThreadLocal 的依賴
 */
@Data
public abstract class AbstractSbeModel implements BytesMarshallable {
    public static final int HEADER_SIZE = 8;
    
    protected long seq;
    protected final PointerBytesStore pointBytesStore = new PointerBytesStore();

    // 實例化編解碼工具，提升可讀性與性能 (避免 ThreadLocal 查找)
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final UnsafeBuffer internalBuffer = new UnsafeBuffer(0, 0);
    protected final NativeUnsafeBuffer encodeBuffer = new NativeUnsafeBuffer(256);

    public void setGatewaySeq(long val) { this.seq = val; }
    public long getGatewaySeq() { return this.seq; }

    protected void wrapHeader(MutableDirectBuffer buffer, int tid, int bl, int sid, int ver) {
        headerEncoder.wrap(buffer, 0).templateId(tid).blockLength(bl).schemaId(sid).version(ver);
    }

    protected DirectBuffer wrapStore(PointerBytesStore store) {
        internalBuffer.wrap(store.addressForRead(0), (int) store.readRemaining());
        return internalBuffer;
    }

    public void fillFromEncodeBuffer(int length) {
        fillFrom(encodeBuffer.buffer(), 0, length);
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
