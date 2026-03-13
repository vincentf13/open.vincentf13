package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.wire.Marshallable;

/**
 * 下單指令 (包裹原始 SBE 數據)
 */
@Data
public class OrderCreateCommand implements Marshallable {
    private long seq;
}
