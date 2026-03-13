package open.vincentf13.service.spot.infra.alloc;

import open.vincentf13.service.spot.infra.Constants.MsgType;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Aeron 充值訊息佈局: [Int: MsgType(4)] [Long: Seq(8)] [Long: UserId(8)] [Int: AssetId(4)] [Long: Amount(8)]
 */
public class AeronDeposit {
    public static final int LENGTH = 32;
    private static final int MSG_TYPE_OFF = 0;
    private static final int SEQ_OFF = 4;
    private static final int USER_ID_OFF = 12;
    private static final int ASSET_ID_OFF = 20;
    private static final int AMOUNT_OFF = 24;

    public void pack(MutableDirectBuffer buffer, int offset, long seq, long userId, int assetId, long amount) {
        buffer.putInt(offset + MSG_TYPE_OFF, MsgType.DEPOSIT);
        buffer.putLong(offset + SEQ_OFF, seq);
        buffer.putLong(offset + USER_ID_OFF, userId);
        buffer.putInt(offset + ASSET_ID_OFF, assetId);
        buffer.putLong(offset + AMOUNT_OFF, amount);
    }

    public long getSeq(DirectBuffer buffer, int offset) { return buffer.getLong(offset + SEQ_OFF); }
    public long getUserId(DirectBuffer buffer, int offset) { return buffer.getLong(offset + USER_ID_OFF); }
    public int getAssetId(DirectBuffer buffer, int offset) { return buffer.getInt(offset + ASSET_ID_OFF); }
    public long getAmount(DirectBuffer buffer, int offset) { return buffer.getLong(offset + AMOUNT_OFF); }
}
