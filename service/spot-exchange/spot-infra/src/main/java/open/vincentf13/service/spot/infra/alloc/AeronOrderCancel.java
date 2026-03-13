package open.vincentf13.service.spot.infra.alloc;

import open.vincentf13.service.spot.infra.Constants.MsgType;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Aeron 撤單訊息佈局: [Int: MsgType(4)] [Long: Seq(8)] [Long: UserId(8)] [Long: OrderId(8)]
 */
public class AeronOrderCancel {
    public static final int LENGTH = 28;
    private static final int MSG_TYPE_OFF = 0;
    private static final int SEQ_OFF = 4;
    private static final int USER_ID_OFF = 12;
    private static final int ORDER_ID_OFF = 20;

    public void pack(MutableDirectBuffer buffer, int offset, long seq, long userId, long orderId) {
        buffer.putInt(offset + MSG_TYPE_OFF, MsgType.ORDER_CANCEL);
        buffer.putLong(offset + SEQ_OFF, seq);
        buffer.putLong(offset + USER_ID_OFF, userId);
        buffer.putLong(offset + ORDER_ID_OFF, orderId);
    }

    public long getSeq(DirectBuffer buffer, int offset) { return buffer.getLong(offset + SEQ_OFF); }
    public long getUserId(DirectBuffer buffer, int offset) { return buffer.getLong(offset + USER_ID_OFF); }
    public long getOrderId(DirectBuffer buffer, int offset) { return buffer.getLong(offset + ORDER_ID_OFF); }
}
