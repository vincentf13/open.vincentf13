package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderStatus;

/** 訂單成交回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderMatchReport extends AbstractExecutionReport {
    public void encode(long timestamp, long userId, long orderId, OrderStatus status, long lastPrice, long lastQty, long cumQty, long avgPrice, long clientOrderId) {
        encodeReport(timestamp, userId, orderId, status, lastPrice, lastQty, cumQty, avgPrice, clientOrderId);
    }
}
