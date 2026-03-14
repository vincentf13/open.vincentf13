package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesOut;

/**
 * 用戶認證回報
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthReport extends AbstractMarshallableModel {
    private long userId;

    @Override
    public void fillFrom(open.vincentf13.service.spot.infra.alloc.aeron.AbstractAeronAlloc<?> aeron) {
        super.fillFrom(aeron);
        this.userId = aeron.readPayloadLong(0);
    }

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(seq);
        bytes.writeLong(userId);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        seq = bytes.readLong();
        userId = bytes.readLong();
    }
}
