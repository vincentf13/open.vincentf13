package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;

/**
 * 充值指令
 */
@Data
public class DepositCommand implements Marshallable {
    private long seq;
    private long userId;
    private int assetId;
    private long amount;

    @Override
    public void writeMarshallable(WireOut wire) {
        wire.write("seq").int64(seq);
        wire.write("userId").int64(userId);
        wire.write("assetId").int32(assetId);
        wire.write("amount").int64(amount);
    }

    @Override
    public void readMarshallable(WireIn wire) {
        seq = wire.read("seq").int64();
        userId = wire.read("userId").int64();
        assetId = wire.read("assetId").int32();
        amount = wire.read("amount").int64();
    }
}
