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
 * 統一 SBE 模型基類 (Unified Off-Heap View)
 * 佈局：[4 bytes MsgType] + [8 bytes Sequence] + [8 bytes SBE Header] + [SBE Body]
 */
@Data
public abstract class AbstractSbeModel implements BytesMarshallable {
    public static final int TYPE_SIZE = 4;
    public static final int SEQ_SIZE = 8;
    public static final int SBE_HEADER_SIZE = 8;
    
    public static final int TYPE_OFFSET = 0;
    public static final int SEQ_OFFSET = TYPE_OFFSET + TYPE_SIZE;
    public static final int SBE_HEADER_OFFSET = SEQ_OFFSET + SEQ_SIZE;
    public static final int BODY_OFFSET = SBE_HEADER_OFFSET + SBE_HEADER_SIZE;

    protected final UnsafeBuffer buffer = new UnsafeBuffer(0, 0);
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final PointerBytesStore pointer = new PointerBytesStore();

    /** 包裝既有內存 */
    public void wrap(DirectBuffer srcBuffer, int offset, int length) {
        this.buffer.wrap(srcBuffer, offset, length);
        refreshDecoder();
    }

    public void wrap(long address, int length) {
        this.buffer.wrap(address, length);
        refreshDecoder();
    }

    public int getMsgType() { return buffer.getInt(TYPE_OFFSET); }
    public void setMsgType(int type) { buffer.putInt(TYPE_OFFSET, type); }

    public long getSeq() { return buffer.getLong(SEQ_OFFSET); }
    public void setSeq(long seq) { buffer.putLong(SEQ_OFFSET, seq); }

    protected void refreshDecoder() {
        if (buffer.capacity() >= BODY_OFFSET) {
            headerDecoder.wrap(buffer, SBE_HEADER_OFFSET);
            wrapDecoder(buffer, BODY_OFFSET, headerDecoder.blockLength(), headerDecoder.version());
        }
    }

    /** 編碼準備 */
    protected MutableDirectBuffer preEncode(MutableDirectBuffer dstBuffer, int offset, int msgType, long seq, int tid, int bl, int sid, int ver) {
        dstBuffer.putInt(offset + TYPE_OFFSET, msgType);
        dstBuffer.putLong(offset + SEQ_OFFSET, seq);
        headerEncoder.wrap(dstBuffer, offset + SBE_HEADER_OFFSET)
                .templateId(tid).blockLength(bl).schemaId(sid).version(ver);
        return dstBuffer;
    }

    protected abstract void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version);
    public abstract int encodedLength();

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        pointer.set(buffer.addressOffset(), buffer.capacity());
        bytes.write(pointer);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        // 從 Chronicle 讀取時，需要外部告知長度（或利用讀取上下文）
        // 這裡暫定一個簡單實現，具體依調用方 dc.wire().read(Key).bytes(model) 為準
        long addr = bytes.addressForRead(bytes.readPosition());
        int len = (int) bytes.readRemaining(); 
        wrap(addr, len);
        bytes.readSkip(len);
    }
    
    public PointerBytesStore getPointBytesStore() {
        pointer.set(buffer.addressOffset(), buffer.capacity());
        return pointer;
    }
}
