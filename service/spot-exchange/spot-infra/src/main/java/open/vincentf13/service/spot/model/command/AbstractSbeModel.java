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
 * <p>
 * 本類別採用 Flyweight 模式，作為堆外內存的「視圖」。它不擁有內存，而是通過包裝既有內存地址來實現零拷貝編解碼。
 * </p>
 * 
 * <h3>使用範例：</h3>
 * 
 * <b>1. 寫入模式 (Write Mode):</b>
 * <pre>{@code
 *     OrderCreateCommand cmd = ctx.getOrderCreateCommand();
 *     cmd.wrapWriteBuffer(dstBuffer, 0)
 *        .set(sequence, timestamp, userId, symbolId, price, qty, side, clientOrderId);
 *     wal.acquireAppender().writeDocument(cmd);
 * }</pre>
 * 
 * <b>2. 讀取模式 (Read Mode):</b>
 * <pre>{@code
 *     // 接收端拿到內存地址後
 *     AbstractSbeModel model = ctx.getCommand(msgType);
 *     model.wrap(address, length); // 對準地址，內部自動觸發 SBE 解碼
 *     
 *     // 直接透過模型 Getter 訪問字段
 *     long userId = model.getUserId();
 *     long price = model.getPrice();
 * }</pre>
 * 
 * 內存佈局 (32 bytes Header):
 * [0-3]   MsgType | [4-7] Padding | [8-15]  Seq | [16-23] GatewayTime | [24-31] SBE Header | [32-...] Body
 */
@Data
public abstract class AbstractSbeModel implements BytesMarshallable {
    public static final int TYPE_SIZE = 4;
    public static final int SEQ_SIZE = 8;
    public static final int GATEWAY_TIME_SIZE = 8;
    public static final int SBE_HEADER_SIZE = 8;
    
    public static final int TYPE_OFFSET = 0;
    public static final int SEQ_OFFSET = 8; // 8-byte aligned
    public static final int GATEWAY_TIME_OFFSET = 16; // 8-byte aligned
    public static final int SBE_HEADER_OFFSET = 24; // 8-byte aligned
    public static final int BODY_OFFSET = 32; // 8-byte aligned

    @Getter
    protected final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(0, 0);
    
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final PointerBytesStore pointerBytesStore = new PointerBytesStore();

    /** 包裝來自 Aeron 的數據緩衝區 (讀取模式) */
    public void wrapAeronReadBuffer(DirectBuffer srcBuffer, int offset, int length) {
        this.unsafeBuffer.wrap(srcBuffer, offset, length);
        refreshDecoder();
    }

    /** 
     * 通用包裝：將模型視圖對準指定的堆外地址 (讀取模式) 
     * 內部自動處理解碼器狀態刷新。
     */
    public void wrap(long address, long length) {
        int safeLength = (int) Math.min(length, Integer.MAX_VALUE);
        this.unsafeBuffer.wrap(address, safeLength);
        refreshDecoder();
    }

    public int getMsgType() { return unsafeBuffer.getInt(TYPE_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN); }
    public long getSeq() { return unsafeBuffer.getLong(SEQ_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN); }

    protected void refreshDecoder() {
        if (unsafeBuffer.capacity() >= BODY_OFFSET) {
            headerDecoder.wrap(unsafeBuffer, SBE_HEADER_OFFSET);
            decoderReWrap(unsafeBuffer, BODY_OFFSET, headerDecoder.blockLength(), headerDecoder.version());
        }
    }

    protected void fillHeader(int msgType, long sequence, int templateId, int blockLength, int schemaId, int version) {
        unsafeBuffer.putInt(TYPE_OFFSET, msgType, java.nio.ByteOrder.LITTLE_ENDIAN);
        unsafeBuffer.putLong(SEQ_OFFSET, sequence, java.nio.ByteOrder.LITTLE_ENDIAN);
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
