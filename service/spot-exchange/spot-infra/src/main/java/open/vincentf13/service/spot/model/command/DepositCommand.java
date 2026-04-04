package open.vincentf13.service.spot.model.command;

import open.vincentf13.service.spot.sbe.DepositDecoder;
import org.agrona.DirectBuffer;

/** DEPOSIT 指令解碼器 (Flyweight) */
public class DepositCommand extends AbstractSbeModel {
    private final DepositDecoder decoder = new DepositDecoder();

    @Override protected void decoderReWrap(DirectBuffer buffer, int offset, int blockLength, int version) {
        decoder.wrap(buffer, offset, blockLength, version);
    }

    public long getUserId() { return decoder.userId(); }
    public int getAssetId() { return decoder.assetId(); }
    public long getAmount() { return decoder.amount(); }
    public long getTimestamp() { return decoder.timestamp(); }
}
