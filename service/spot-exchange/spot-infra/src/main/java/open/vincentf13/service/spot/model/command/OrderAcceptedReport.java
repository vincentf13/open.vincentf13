package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 訂單接受回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderAcceptedReport extends AbstractExecutionReport {
    public void encode(MutableDirectBuffer buffer, int offset, long seq, long timestamp, long userId, long orderId, long clientOrderId) {
        encodeReport(buffer, offset, MsgType.ORDER_ACCEPTED, seq, timestamp, userId, orderId, OrderStatus.NEW, 0, 0, 0, 0, clientOrderId);
    }
}
