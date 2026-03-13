package open.vincentf13.service.spot.infra.alloc.aeron;

import open.vincentf13.service.spot.infra.Constants.MsgType;

/**
 * Aeron 充值訊息
 */
public class AeronDeposit extends AbstractAeronAlloc<AeronDeposit> {
    public static final int USER_ID_OFF = HEADER_LENGTH;
    public static final int ASSET_ID_OFF = USER_ID_OFF + 8;
    public static final int AMOUNT_OFF = ASSET_ID_OFF + 4;
    public static final int LENGTH = HEADER_LENGTH + 8 + 4 + 8;

    /** 打包充值訊息 (需先調用 wrap) */
    public void write(long seq, long userId, int assetId, long amount) {
        writeHeader(MsgType.DEPOSIT, seq);
        mutableBuffer.putLong(offset + USER_ID_OFF, userId);
        mutableBuffer.putInt(offset + ASSET_ID_OFF, assetId);
        mutableBuffer.putLong(offset + AMOUNT_OFF, amount);
    }

    public long readUserId() { return buffer.getLong(offset + USER_ID_OFF); }
    public int readAssetId() { return buffer.getInt(offset + ASSET_ID_OFF); }
    public long readAmount() { return buffer.getLong(offset + AMOUNT_OFF); }
}
