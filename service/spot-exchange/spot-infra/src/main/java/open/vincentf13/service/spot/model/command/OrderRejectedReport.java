package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 訂單拒絕回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderRejectedReport extends AbstractExecutionReport {
    public void encode(MutableDirectBuffer buffer, int offset, long seq, long timestamp, long userId, long clientOrderId) {
        encodeReport(buffer, offset, MsgType.ORDER_REJECTED, seq, timestamp, userId, 0, OrderStatus.REJECTED, 0, 0, 0, 0, clientOrderId);
    }
}
