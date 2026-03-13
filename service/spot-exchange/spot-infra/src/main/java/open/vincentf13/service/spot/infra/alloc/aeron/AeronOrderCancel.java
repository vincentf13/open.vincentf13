package open.vincentf13.service.spot.infra.alloc.aeron;

import open.vincentf13.service.spot.infra.Constants.MsgType;

/**
 * Aeron 撤單訊息
 */
public class AeronOrderCancel extends AbstractAeronAlloc<AeronOrderCancel> {
    public static final int USER_ID_OFF = HEADER_LENGTH;
    public static final int ORDER_ID_OFF = USER_ID_OFF + 8;
    public static final int LENGTH = HEADER_LENGTH + 8 + 8;

    /** 打包撤單訊息 (需先調用 wrap) */
    public void write(long seq, long userId, long orderId) {
        writeHeader(MsgType.ORDER_CANCEL, seq);
        mutableBuffer.putLong(offset + USER_ID_OFF, userId);
        mutableBuffer.putLong(offset + ORDER_ID_OFF, orderId);
    }

    public long readUserId() { return buffer.getLong(offset + USER_ID_OFF); }
    public long readOrderId() { return buffer.getLong(offset + ORDER_ID_OFF); }
}
