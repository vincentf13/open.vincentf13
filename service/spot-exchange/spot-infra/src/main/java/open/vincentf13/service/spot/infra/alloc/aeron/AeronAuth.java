package open.vincentf13.service.spot.infra.alloc.aeron;

import open.vincentf13.service.spot.infra.Constants.MsgType;

/**
 * Aeron 認證訊息
 */
public class AeronAuth extends AbstractAeronAlloc<AeronAuth> {
    public static final int USER_ID_OFF = HEADER_LENGTH;
    public static final int LENGTH = HEADER_LENGTH + 8;

    /** 打包認證訊息 (需先調用 wrap) */
    public void write(long seq, long userId) {
        writeHeader(MsgType.AUTH, seq);
        mutableBuffer.putLong(offset + USER_ID_OFF, userId);
    }

    public long readUserId() {
        return buffer.getLong(offset + USER_ID_OFF);
    }
}
