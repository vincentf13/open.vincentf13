package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesOut;

/**
 * 充值回報
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DepositReport extends AbstractMarshallableModel {
    private long userId;
    private int assetId;
    private long amount;

    @Override
    public void fillFrom(open.vincentf13.service.spot.infra.alloc.aeron.AbstractAeronAlloc<?> aeron) {
        super.fillFrom(aeron);
        this.userId = aeron.readPayloadLong(0);
        this.assetId = aeron.readPayloadInt(8);
        this.amount = aeron.readPayloadLong(12);
    }

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(seq);
        bytes.writeLong(userId);
        bytes.writeInt(assetId);
        bytes.writeLong(amount);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        seq = bytes.readLong();
        userId = bytes.readLong();
        assetId = bytes.readInt();
        amount = bytes.readLong();
    }
}
