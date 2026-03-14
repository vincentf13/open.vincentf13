package open.vincentf13.service.spot.infra.alloc.aeron;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Aeron 訊息佈局抽象基類 (優化版)
 * 封裝了 Buffer, Offset 與 Length，提供更簡潔的數據訪問 API
 */
@SuppressWarnings("unchecked")
public abstract class AbstractAeronAlloc<T extends AbstractAeronAlloc<T>> {
    public static final int MSG_TYPE_OFF = 0;
    public static final int SEQ_OFF = 4;
    public static final int HEADER_LENGTH = 12;

    protected DirectBuffer buffer;
    protected MutableDirectBuffer mutableBuffer;
    protected int offset;
    protected int totalLength;

    /** 綁定唯讀 Buffer 並指定總長度 */
    public T wrap(DirectBuffer buffer, int offset, int totalLength) {
        this.buffer = buffer;
        this.offset = offset;
        this.totalLength = totalLength;
        return (T) this;
    }

    /** 綁定可寫 Buffer (長度可選) */
    public T wrap(MutableDirectBuffer buffer, int offset) {
        this.mutableBuffer = buffer;
        this.buffer = buffer;
        this.offset = offset;
        return (T) this;
    }

    /** 獲取基礎地址 (用於低階拷貝) */
    public long getBufferAddress() { return buffer.addressOffset(); }

    /** 獲取消息類型 */
    public int readMsgType() { return buffer.getInt(offset + MSG_TYPE_OFF); }

    /** 獲取全局序號 */
    public long readSeq() { return buffer.getLong(offset + SEQ_OFF); }

    /** 獲取 Payload 起始偏移量 */
    public int getPayloadOffset() { return offset + HEADER_LENGTH; }

    /** 獲取 Payload 長度 */
    public int getPayloadLength() { return totalLength - HEADER_LENGTH; }

    /** 從指定偏移量讀取 Payload 中的 Long */
    public long readPayloadLong(int internalOffset) {
        return buffer.getLong(getPayloadOffset() + internalOffset);
    }

    /** 從指定偏移量讀取 Payload 中的 Int */
    public int readPayloadInt(int internalOffset) {
        return buffer.getInt(getPayloadOffset() + internalOffset);
    }

    /** 從指定偏移量讀取 Payload 中的 Byte */
    public byte readPayloadByte(int internalOffset) {
        return buffer.getByte(getPayloadOffset() + internalOffset);
    }

    /** 寫入共有頭部 */
    protected void writeHeader(int msgType, long seq) {
        mutableBuffer.putInt(offset + MSG_TYPE_OFF, msgType);
        mutableBuffer.putLong(offset + SEQ_OFF, seq);
    }
}
