package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.wire.Marshallable;

/**
 * 撤單指令
 */
@Data
public class OrderCancelCommand implements Marshallable {
    private long seq;
    private long userId;
    private long orderId;
}
