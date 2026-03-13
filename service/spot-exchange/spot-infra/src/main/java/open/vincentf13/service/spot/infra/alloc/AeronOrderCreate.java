package open.vincentf13.service.spot.infra.alloc;

import open.vincentf13.service.spot.infra.Constants.MsgType;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Aeron 下單訊息佈局: [Int: MsgType(4)] [Long: Seq(8)] [Payload: SBE(N)]
 */
public class AeronOrderCreate {
    public static final int HEADER_LENGTH = 12;
    private static final int MSG_TYPE_OFF = 0;
    private static final int SEQ_OFF = 4;
    private static final int PAYLOAD_OFF = 12;

    public void pack(MutableDirectBuffer buffer, int offset, long seq, DirectBuffer payload, int payloadOffset, int payloadLen) {
        buffer.putInt(offset + MSG_TYPE_OFF, MsgType.ORDER_CREATE);
        buffer.putLong(offset + SEQ_OFF, seq);
        buffer.putBytes(offset + PAYLOAD_OFF, payload, payloadOffset, payloadLen);
    }

    public long getSeq(DirectBuffer buffer, int offset) { return buffer.getLong(offset + SEQ_OFF); }
    public int getPayloadOffset(int baseOffset) { return baseOffset + PAYLOAD_OFF; }
}
