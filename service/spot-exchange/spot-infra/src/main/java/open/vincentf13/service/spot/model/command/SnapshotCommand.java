package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.wire.Marshallable;

/**
 * 快照指令
 */
@Data
public class SnapshotCommand implements Marshallable {
    private long seq;
}
