package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.ExecutionReportDecoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.DirectBuffer;

/**
 * 訂單撤單回報
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCanceledReport extends AbstractSbeModel {
    private final ExecutionReportDecoder decoder = new ExecutionReportDecoder();

    public ExecutionReportDecoder decode() {
        DirectBuffer buffer = wrapStore();
        headerDecoder.wrap(buffer, 0);
        return decoder.wrap(buffer, HEADER_SIZE, headerDecoder.blockLength(), headerDecoder.version());
    }

    public void encode(long timestamp, long userId, long orderId, long filledQuantity, long clientOrderId) {
        encodeReport(timestamp, userId, orderId, OrderStatus.CANCELED, 0, 0, filledQuantity, 0, clientOrderId);
    }

    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
    public long getOrderId() { return decoder.orderId(); }
    public OrderStatus getStatus() { return decoder.status(); }
    public long getCumQty() { return decoder.cumQty(); }
    public long getClientOrderId() { return decoder.clientOrderId(); }
}
