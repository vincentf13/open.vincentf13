package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

/**
 * 用戶認證回報
 */
@Data
public class AuthReport implements BytesMarshallable {
    private long gatewaySeq;
    private long userId;

    public void fillFrom(open.vincentf13.service.spot.infra.alloc.aeron.AbstractAeronAlloc<?> aeron) {
        this.gatewaySeq = aeron.readSeq();
        this.userId = aeron.readPayloadLong(0);
    }

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(gatewaySeq);
        bytes.writeLong(userId);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        gatewaySeq = bytes.readLong();
        userId = bytes.readLong();
    }
}
