package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;

/**
 * 快照指令
 */
@Data
public class SnapshotCommand implements Marshallable {
    private long seq;

    @Override
    public void writeMarshallable(WireOut wire) {
        wire.write("seq").int64(seq);
    }

    @Override
    public void readMarshallable(WireIn wire) {
        seq = wire.read("seq").int64();
    }
}
