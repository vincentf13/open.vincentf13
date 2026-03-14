package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.DepositDecoder;
import open.vincentf13.service.spot.sbe.DepositEncoder;
import org.agrona.DirectBuffer;

/** 充值指令 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DepositCommand extends AbstractSbeModel {
    private final DepositEncoder encoder = new DepositEncoder();
    private final DepositDecoder decoder = new DepositDecoder();

    @Override
    protected void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version) {
        decoder.wrap(buffer, offset, blockLength, version);
    }

    public void encode(long timestamp, long userId, int assetId, long amount) {
        encoder.wrap(preEncode(DepositEncoder.TEMPLATE_ID, DepositEncoder.BLOCK_LENGTH, DepositEncoder.SCHEMA_ID, DepositEncoder.SCHEMA_VERSION), HEADER_SIZE)
                .timestamp(timestamp).userId(userId).assetId(assetId).amount(amount);
        postEncode(encoder.encodedLength());
    }

    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
    public int getAssetId() { return decoder.assetId(); }
    public long getAmount() { return decoder.amount(); }
}
