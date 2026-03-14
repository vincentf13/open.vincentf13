package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.DepositDecoder;
import open.vincentf13.service.spot.sbe.DepositEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * 充值指令
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DepositCommand extends AbstractSbeModel {
    private final DepositEncoder encoder = new DepositEncoder();
    private final DepositDecoder decoder = new DepositDecoder();

    public DepositDecoder decode() {
        DirectBuffer buffer = wrapStore(pointBytesStore);
        headerDecoder.wrap(buffer, 0);
        return decoder.wrap(buffer, HEADER_SIZE, headerDecoder.blockLength(), headerDecoder.version());
    }

    public void encode(long timestamp, long userId, int assetId, long amount) {
        MutableDirectBuffer buffer = encodeBuffer.wrapForWrite();
        wrapHeader(buffer, DepositEncoder.TEMPLATE_ID, DepositEncoder.BLOCK_LENGTH, DepositEncoder.SCHEMA_ID, DepositEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(timestamp).userId(userId).assetId(assetId).amount(amount);
        fillFromEncodeBuffer(HEADER_SIZE + encoder.encodedLength());
    }
}
