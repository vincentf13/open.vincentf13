package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderStatus;

/** 訂單撤單回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCanceledReport extends AbstractExecutionReport {
    public void encode(long timestamp, long userId, long orderId, long filledQuantity, long clientOrderId) {
        encodeReport(timestamp, userId, orderId, OrderStatus.CANCELED, 0, 0, filledQuantity, 0, clientOrderId);
    }
}
