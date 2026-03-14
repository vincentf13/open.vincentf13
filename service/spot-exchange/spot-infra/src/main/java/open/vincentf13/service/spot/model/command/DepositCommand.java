package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.DepositDecoder;
import open.vincentf13.service.spot.sbe.DepositEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 統一格式充值指令 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DepositCommand extends AbstractSbeModel {
    private final DepositEncoder encoder = new DepositEncoder();
    private final DepositDecoder decoder = new DepositDecoder();

    @Override protected void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version) { decoder.wrap(buffer, offset, blockLength, version); }

    public DepositCommand write(MutableDirectBuffer dstBuffer, int offset) {
        this.buffer.wrap(dstBuffer, offset, encodedLength());
        return this;
    }

    public void set(long seq, long timestamp, long userId, int assetId, long amount) {
        fillCommonHeader(MsgType.DEPOSIT, seq, DepositEncoder.TEMPLATE_ID, DepositEncoder.BLOCK_LENGTH, DepositEncoder.SCHEMA_ID, DepositEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, BODY_OFFSET).timestamp(timestamp).userId(userId).assetId(assetId).amount(amount);
        refreshDecoder();
    }

    @Override public int encodedLength() { return BODY_OFFSET + DepositEncoder.BLOCK_LENGTH; }
    public long getUserId() { return decoder.userId(); }
    public int getAssetId() { return decoder.assetId(); }
    public long getAmount() { return decoder.amount(); }
    public long getTimestamp() { return decoder.timestamp(); }
}
