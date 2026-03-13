package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;

/**
 * 撤單指令
 */
@Data
public class OrderCancelCommand implements Marshallable {
    private long seq;
    private long userId;
    private long orderId;

    @Override
    public void writeMarshallable(WireOut wire) {
        wire.write("seq").int64(seq);
        wire.write("userId").int64(userId);
        wire.write("orderId").int64(orderId);
    }

    @Override
    public void readMarshallable(WireIn wire) {
        seq = wire.read("seq").int64();
        userId = wire.read("userId").int64();
        orderId = wire.read("orderId").int64();
    }
}
