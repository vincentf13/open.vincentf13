package open.vincentf13.service.spot.infra.alloc;

import open.vincentf13.service.spot.infra.Constants.MsgType;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Aeron 認證訊息佈局: [Int: MsgType(4)] [Long: Seq(8)] [Long: UserId(8)]
 */
public class AeronAuth {
    public static final int LENGTH = 20;
    private static final int MSG_TYPE_OFF = 0;
    private static final int SEQ_OFF = 4;
    private static final int USER_ID_OFF = 12;

    public void pack(MutableDirectBuffer buffer, int offset, long seq, long userId) {
        buffer.putInt(offset + MSG_TYPE_OFF, MsgType.AUTH);
        buffer.putLong(offset + SEQ_OFF, seq);
        buffer.putLong(offset + USER_ID_OFF, userId);
    }

    public long getSeq(DirectBuffer buffer, int offset) { return buffer.getLong(offset + SEQ_OFF); }
    public long getUserId(DirectBuffer buffer, int offset) { return buffer.getLong(offset + USER_ID_OFF); }
}
