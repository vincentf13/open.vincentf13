package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 訂單撤單回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCanceledReport extends AbstractExecutionReport {
    public void encode(MutableDirectBuffer buffer, int offset, long seq, long timestamp, long userId, long orderId, long filledQuantity, long clientOrderId) {
        encodeReport(buffer, offset, MsgType.ORDER_CANCELED, seq, timestamp, userId, orderId, OrderStatus.CANCELED, 0, 0, filledQuantity, 0, clientOrderId);
    }
}
