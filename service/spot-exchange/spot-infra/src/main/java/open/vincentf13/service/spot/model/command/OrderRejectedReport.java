package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderStatus;

/** 訂單拒絕回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderRejectedReport extends AbstractExecutionReport {
    public void encode(long timestamp, long userId, long clientOrderId) {
        encodeReport(timestamp, userId, 0, OrderStatus.REJECTED, 0, 0, 0, 0, clientOrderId);
    }
}
