package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.DepositDecoder;
import open.vincentf13.service.spot.sbe.DepositEncoder;
import org.agrona.DirectBuffer;

/**
 * 充值指令 (實例私有編解碼器)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DepositCommand extends AbstractSbeModel {
    private final DepositEncoder encoder = new DepositEncoder();
    private final DepositDecoder decoder = new DepositDecoder();

    public DepositDecoder decode() {
        DirectBuffer buffer = wrapStore();
        headerDecoder.wrap(buffer, 0);
        return decoder.wrap(buffer, HEADER_SIZE, headerDecoder.blockLength(), headerDecoder.version());
    }

    public void encode(long timestamp, long userId, int assetId, long amount) {
        wrapHeader(DepositEncoder.TEMPLATE_ID, DepositEncoder.BLOCK_LENGTH, DepositEncoder.SCHEMA_ID, DepositEncoder.SCHEMA_VERSION);
        encoder.wrap(selfBuffer, HEADER_SIZE).timestamp(timestamp).userId(userId).assetId(assetId).amount(amount);
        int totalLength = HEADER_SIZE + encoder.encodedLength();
        this.pointBytesStore.set(selfBuffer.addressOffset(), totalLength);
    }

    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
    public int getAssetId() { return decoder.assetId(); }
    public long getAmount() { return decoder.amount(); }
}
