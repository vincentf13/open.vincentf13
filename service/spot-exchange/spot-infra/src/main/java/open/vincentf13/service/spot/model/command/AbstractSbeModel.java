package open.vincentf13.service.spot.model.command;

import lombok.Data;
import open.vincentf13.service.spot.sbe.MessageHeaderDecoder;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * SBE 指令解碼基類 (Flyweight Read-Only View)
 *
 * 本類作為堆外內存的「視圖」，透過 wrap() 對準地址後，
 * 內部 SBE 解碼器自動刷新，子類透過 getter 提供型別安全的欄位存取。
 *
 * 記憶體佈局 (32 bytes Header):
 * [0-3] MsgType | [4-7] Padding | [8-15] Seq | [16-23] GatewayTime | [24-31] SBE Header | [32+] Body
 */
@Data
public abstract class AbstractSbeModel {
    public static final int TYPE_OFFSET = 0;
    public static final int SEQ_OFFSET = 8;
    public static final int GATEWAY_TIME_OFFSET = 16;
    public static final int SBE_HEADER_OFFSET = 24;
    public static final int BODY_OFFSET = 32;

    protected final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(0, 0);
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    /** 將視圖對準指定堆外地址，內部自動刷新 SBE 解碼器 */
    public void wrap(long address, long length) {
        unsafeBuffer.wrap(address, (int) Math.min(length, Integer.MAX_VALUE));
        if (unsafeBuffer.capacity() >= BODY_OFFSET) {
            headerDecoder.wrap(unsafeBuffer, SBE_HEADER_OFFSET);
            decoderReWrap(unsafeBuffer, BODY_OFFSET, headerDecoder.blockLength(), headerDecoder.version());
        }
    }

    public int getMsgType() { return unsafeBuffer.getInt(TYPE_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN); }
    public long getSeq() { return unsafeBuffer.getLong(SEQ_OFFSET, java.nio.ByteOrder.LITTLE_ENDIAN); }

    protected abstract void decoderReWrap(DirectBuffer buffer, int offset, int blockLength, int version);
}
