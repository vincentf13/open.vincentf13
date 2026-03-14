package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesMarshallable;

/**
 * 純序列化模型的抽象基類
 */
@Data
public abstract class AbstractMarshallableModel implements BytesMarshallable {
    protected long seq;

    public void setGatewaySeq(long val) { this.seq = val; }
    public long getGatewaySeq() { return this.seq; }

    public void fillFrom(open.vincentf13.service.spot.infra.alloc.aeron.AbstractAeronAlloc<?> aeron) {
        this.seq = aeron.readSeq();
    }
}
