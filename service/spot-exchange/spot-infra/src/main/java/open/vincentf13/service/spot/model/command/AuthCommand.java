package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.wire.Marshallable;

/**
 * 用戶認證指令
 */
@Data
public class AuthCommand implements Marshallable {
    private long seq;
    private long userId;
}
