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
 * 職責：作為堆外內存的 Flyweight 視圖，提供零拷貝的編解碼與序列化能力。
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

    /** 包裝來自 Aeron 的數據緩衝區 */
    public void wrapAeronBuffer(DirectBuffer srcBuffer, int offset, int length) {
        this.unsafeBuffer.wrap(srcBuffer, offset, length);
        refreshDecoder();
    }

    /** 獲取消息類型 */
    public int getMsgType() { return unsafeBuffer.getInt(TYPE_OFFSET); }
    /** 獲取序列號 */
    public long getSeq() { return unsafeBuffer.getLong(SEQ_OFFSET); }

    /** 刷新內部解碼器狀態 */
    protected void refreshDecoder() {
        if (unsafeBuffer.capacity() >= BODY_OFFSET) {
            headerDecoder.wrap(unsafeBuffer, SBE_HEADER_OFFSET);
            decoderReWrap(unsafeBuffer, BODY_OFFSET, headerDecoder.blockLength(), headerDecoder.version());
        }
    }

    /** 填充消息頭部 (MsgType + Seq + SBE Header) */
    protected void fillHeader(int msgType, long sequence, int templateId, int blockLength, int schemaId, int version) {
        unsafeBuffer.putInt(TYPE_OFFSET, msgType);
        unsafeBuffer.putLong(SEQ_OFFSET, sequence);
        headerEncoder.wrap(unsafeBuffer, SBE_HEADER_OFFSET)
                .templateId(templateId).blockLength(blockLength).schemaId(schemaId).version(version);
    }

    /** 安裝（重新包裝）具體的業務 SBE 解碼器 */
    protected abstract void decoderReWrap(DirectBuffer buffer, int offset, int blockLength, int version);
    
    /** 內存總長度 */
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
        this.unsafeBuffer.wrap(addr, len);
        refreshDecoder();
        bytes.readSkip(len);
    }
}
