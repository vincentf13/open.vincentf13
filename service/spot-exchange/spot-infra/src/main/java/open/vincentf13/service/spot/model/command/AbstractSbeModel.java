package open.vincentf13.service.spot.model.command;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.PointerBytesStore;
import open.vincentf13.service.spot.sbe.MessageHeaderDecoder;
import open.vincentf13.service.spot.sbe.MessageHeaderEncoder;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * 統一 SBE 模型基類 (Unified Off-Heap View)
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

    @Getter(AccessLevel.PROTECTED)
    protected final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(0, 0);
    
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final PointerBytesStore pointerBytesStore = new PointerBytesStore();

    /** 包裝來自 Aeron 的數據緩衝區 */
    public void wrapAeronReadBuffer(DirectBuffer srcBuffer, int offset, int length) {
        this.unsafeBuffer.wrap(srcBuffer, offset, length);
        refreshDecoder();
    }

    /** 
     * 通用包裝：對準某個堆外地址
     * 內部自動處理解碼器狀態更新。
     */
    public void wrap(long address, long length) {
        int safeLength = (int) Math.min(length, Integer.MAX_VALUE);
        this.unsafeBuffer.wrap(address, safeLength);
        refreshDecoder();
    }

    public int getMsgType() { return unsafeBuffer.getInt(TYPE_OFFSET); }
    public long getSeq() { return unsafeBuffer.getLong(SEQ_OFFSET); }

    protected void refreshDecoder() {
        if (unsafeBuffer.capacity() >= BODY_OFFSET) {
            headerDecoder.wrap(unsafeBuffer, SBE_HEADER_OFFSET);
            decoderReWrap(unsafeBuffer, BODY_OFFSET, headerDecoder.blockLength(), headerDecoder.version());
        }
    }

    protected void fillHeader(int msgType, long sequence, int templateId, int blockLength, int schemaId, int version) {
        unsafeBuffer.putInt(TYPE_OFFSET, msgType);
        unsafeBuffer.putLong(SEQ_OFFSET, sequence);
        headerEncoder.wrap(unsafeBuffer, SBE_HEADER_OFFSET)
                .templateId(templateId).blockLength(blockLength).schemaId(schemaId).version(version);
    }

    protected abstract void decoderReWrap(DirectBuffer buffer, int offset, int blockLength, int version);
    public abstract int totalByteLength();

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        pointerBytesStore.set(unsafeBuffer.addressOffset(), unsafeBuffer.capacity());
        bytes.write(pointerBytesStore);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        long addr = bytes.addressForRead(bytes.readPosition());
        long len = bytes.readRemaining(); 
        wrap(addr, len);
        bytes.readSkip(len);
    }
}
