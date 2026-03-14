package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 訂單成交回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderMatchReport extends AbstractExecutionReport {
    public void encode(MutableDirectBuffer buffer, int offset, long seq, long timestamp, long userId, long orderId, OrderStatus status, long lastPrice, long lastQty, long cumQty, long avgPrice, long clientOrderId) {
        encodeReport(buffer, offset, MsgType.ORDER_MATCHED, seq, timestamp, userId, orderId, status, lastPrice, lastQty, cumQty, avgPrice, clientOrderId);
    }
}
