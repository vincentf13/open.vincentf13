package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

/**
 * 充值成功回報
 */
@Data
public class DepositReport implements BytesMarshallable {
    private long gatewaySeq;
    private long userId;
    private int assetId;
    private long amount;

    public void fillFrom(open.vincentf13.service.spot.infra.alloc.aeron.AbstractAeronAlloc<?> aeron) {
        this.gatewaySeq = aeron.readSeq();
        this.userId = aeron.readPayloadLong(0);
        this.assetId = (int) aeron.readPayloadLong(8);
        this.amount = aeron.readPayloadLong(12);
    }

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(gatewaySeq);
        bytes.writeLong(userId);
        bytes.writeInt(assetId);
        bytes.writeLong(amount);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        gatewaySeq = bytes.readLong();
        userId = bytes.readLong();
        assetId = bytes.readInt();
        amount = bytes.readLong();
    }
}
