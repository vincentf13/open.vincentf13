package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;

/**
 * 用戶認證指令
 */
@Data
public class AuthCommand implements Marshallable {
    private long seq;
    private long userId;

    @Override
    public void writeMarshallable(WireOut wire) {
        wire.write("seq").int64(seq);
        wire.write("userId").int64(userId);
    }

    @Override
    public void readMarshallable(WireIn wire) {
        seq = wire.read("seq").int64();
        userId = wire.read("userId").int64();
    }
}
