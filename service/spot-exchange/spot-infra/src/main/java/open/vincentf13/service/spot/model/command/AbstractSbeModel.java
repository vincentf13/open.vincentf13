package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.PointerBytesStore;
import open.vincentf13.service.spot.sbe.MessageHeaderDecoder;
import open.vincentf13.service.spot.sbe.MessageHeaderEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * SBE 模型通用抽象基類
 * 僅包含通用的 Header 編解碼工具、實例級緩衝區與基礎序列化邏輯
 */
@Data
public abstract class AbstractSbeModel implements BytesMarshallable {
    public static final int HEADER_SIZE = 8;
    
    protected long seq;
    protected final PointerBytesStore pointBytesStore = new PointerBytesStore();
    protected final MutableDirectBuffer selfBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
    
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final UnsafeBuffer internalBuffer = new UnsafeBuffer(0, 0);

    /** 子類實現：包裝自身的 SBE 解碼器 */
    protected abstract void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version);

    public void setGatewaySeq(long val) { this.seq = val; }
    public long getGatewaySeq() { return this.seq; }

    protected DirectBuffer wrapStore() {
        internalBuffer.wrap(pointBytesStore.addressForRead(0), (int) pointBytesStore.readRemaining());
        return internalBuffer;
    }

    protected void refreshDecoder() {
        if (pointBytesStore.readRemaining() >= HEADER_SIZE) {
            DirectBuffer buffer = wrapStore();
            headerDecoder.wrap(buffer, 0);
            wrapDecoder(buffer, HEADER_SIZE, headerDecoder.blockLength(), headerDecoder.version());
        }
    }

    public void fillFrom(open.vincentf13.service.spot.infra.alloc.aeron.AbstractAeronAlloc<?> aeron) {
        this.seq = aeron.readSeq();
        this.pointBytesStore.set(aeron.getBufferAddress() + aeron.getPayloadOffset(), aeron.getPayloadLength());
        refreshDecoder();
    }

    public void fillFrom(DirectBuffer buffer, int offset, int length) {
        this.pointBytesStore.set(buffer.addressOffset() + offset, length);
        refreshDecoder();
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        seq = bytes.readLong();
        int len = (int) bytes.readStopBit();
        if (len > 0) {
            long address = bytes.addressForRead(bytes.readPosition());
            pointBytesStore.set(address, len);
            bytes.readSkip(len);
            refreshDecoder();
        } else {
            pointBytesStore.set(0, 0);
        }
    }

    /** 編碼準備：寫入 SBE 消息頭 */
    protected MutableDirectBuffer preEncode(int tid, int bl, int sid, int ver) {
        headerEncoder.wrap(selfBuffer, 0).templateId(tid).blockLength(bl).schemaId(sid).version(ver);
        return selfBuffer;
    }

    /** 編碼完成：更新 ByteStore 狀態並觸發自動解碼 */
    protected void postEncode(int bodyLength) {
        int totalLength = HEADER_SIZE + bodyLength;
        this.pointBytesStore.set(selfBuffer.addressOffset(), totalLength);
        refreshDecoder();
    }

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(seq);
        long len = pointBytesStore.readRemaining();
        bytes.writeStopBit(len);
        if (len > 0) bytes.write(pointBytesStore);
    }
}
