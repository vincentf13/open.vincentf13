package open.vincentf13.service.spot.model.command;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.PointerBytesStore;
import open.vincentf13.service.spot.sbe.MessageHeaderDecoder;
import open.vincentf13.service.spot.sbe.MessageHeaderEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * 統一 SBE 模型基類 (Unified Off-Heap View)
 * 佈局：[4 bytes MsgType] + [8 bytes Sequence] + [8 bytes SBE Header] + [SBE Body]
 */
@Data
public abstract class AbstractSbeModel implements BytesMarshallable {
    protected static final int TYPE_SIZE = 4;
    protected static final int SEQ_SIZE = 8;
    protected static final int SBE_HEADER_SIZE = 8;
    
    protected static final int TYPE_OFFSET = 0;
    protected static final int SEQ_OFFSET = TYPE_OFFSET + TYPE_SIZE;
    protected static final int SBE_HEADER_OFFSET = SEQ_OFFSET + SEQ_SIZE;
    protected static final int BODY_OFFSET = SBE_HEADER_OFFSET + SBE_HEADER_SIZE;

    @Getter(AccessLevel.PROTECTED)
    protected final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(0, 0);
    
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final PointerBytesStore pointerBytesStore = new PointerBytesStore();

    /** 包裝既有內存 (讀取模式) */
    public void wrapAeronBuffer(DirectBuffer srcBuffer, int offset, int length) {
        this.unsafeBuffer.wrap(srcBuffer, offset, length);
        refreshDecoder();
    }

    public void wrap(long address, int length) {
        this.unsafeBuffer.wrap(address, length);
        refreshDecoder();
    }

    public int getMsgType() { return unsafeBuffer.getInt(TYPE_OFFSET); }
    public long getSeq() { return unsafeBuffer.getLong(SEQ_OFFSET); }

    protected void refreshDecoder() {
        if (unsafeBuffer.capacity() >= BODY_OFFSET) {
            headerDecoder.wrap(unsafeBuffer, SBE_HEADER_OFFSET);
            wrapDecoder(unsafeBuffer, BODY_OFFSET, headerDecoder.blockLength(), headerDecoder.version());
        }
    }

    /** 寫入通用的 MsgType, Seq 與 SBE Header */
    protected void fillCommonHeader(int msgType, long seq, int tid, int bl, int sid, int ver) {
        unsafeBuffer.putInt(TYPE_OFFSET, msgType);
        unsafeBuffer.putLong(SEQ_OFFSET, seq);
        headerEncoder.wrap(unsafeBuffer, SBE_HEADER_OFFSET)
                .templateId(tid).blockLength(bl).schemaId(sid).version(ver);
    }

    protected abstract void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version);
    
    /** 返回模型在內存中的總長度 (含 Header) */
    public abstract int totalByteLength();

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        pointerBytesStore.set(unsafeBuffer.addressOffset(), unsafeBuffer.capacity());
        bytes.write(pointerBytesStore);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        long addr = bytes.addressForRead(bytes.readPosition());
        int len = (int) bytes.readRemaining(); 
        wrap(addr, len);
        bytes.readSkip(len);
    }
    
    /** 僅供兼容舊代碼，後續建議直接使用 unsafeBuffer 或 pointerBytesStore */
    public PointerBytesStore getPointBytesStore() {
        pointerBytesStore.set(unsafeBuffer.addressOffset(), unsafeBuffer.capacity());
        return pointerBytesStore;
    }
}
