package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderStatus;

/** 訂單接受回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderAcceptedReport extends AbstractExecutionReport {
    public void encode(long timestamp, long userId, long orderId, long clientOrderId) {
        encodeReport(timestamp, userId, orderId, OrderStatus.NEW, 0, 0, 0, 0, clientOrderId);
    }
}
