package open.vincentf13.service.spot.infra.alloc.aeron;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Aeron 訊息佈局抽象基類
 * 統一管理狀態綁定與共有頭部 [Int: MsgType] [Long: Sequence]
 */
@SuppressWarnings("unchecked")
public abstract class AbstractAeronAlloc<T extends AbstractAeronAlloc<T>> {
    public static final int MSG_TYPE_OFF = 0;
    public static final int SEQ_OFF = 4;
    public static final int HEADER_LENGTH = 12;

    protected DirectBuffer buffer;
    protected MutableDirectBuffer mutableBuffer;
    protected int offset;

    /** 綁定唯讀 Buffer */
    public T wrap(DirectBuffer buffer, int offset) {
        this.buffer = buffer;
        this.offset = offset;
        return (T) this;
    }

    /** 綁定可寫 Buffer */
    public T wrap(MutableDirectBuffer buffer, int offset) {
        this.mutableBuffer = buffer;
        this.buffer = buffer;
        this.offset = offset;
        return (T) this;
    }

    /** 獲取消息類型 */
    public int readMsgType() { return buffer.getInt(offset + MSG_TYPE_OFF); }

    /** 獲取全局序號 */
    public long readSeq() { return buffer.getLong(offset + SEQ_OFF); }

    /** 獲取 Payload 起始偏移量 */
    public int getPayloadOffset() { return offset + HEADER_LENGTH; }

    /** 根據總長度獲取 Payload 長度 */
    public int getPayloadLength(int totalLength) { return totalLength - HEADER_LENGTH; }

    /** 寫入共有頭部 */
    protected void writeHeader(int msgType, long seq) {
        mutableBuffer.putInt(offset + MSG_TYPE_OFF, msgType);
        mutableBuffer.putLong(offset + SEQ_OFF, seq);
    }
}
