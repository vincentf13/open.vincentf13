package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.wire.Marshallable;

/**
 * 充值指令
 */
@Data
public class DepositCommand implements Marshallable {
    private long seq;
    private long userId;
    private int assetId;
    private long amount;
}
