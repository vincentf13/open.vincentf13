package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.ExecutionReportDecoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.DirectBuffer;

/**
 * 訂單成交回報
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderMatchReport extends AbstractSbeModel {
    private final ExecutionReportDecoder decoder = new ExecutionReportDecoder();

    public ExecutionReportDecoder decode() {
        DirectBuffer buffer = wrapStore(pointBytesStore);
        headerDecoder.wrap(buffer, 0);
        return decoder.wrap(buffer, HEADER_SIZE, headerDecoder.blockLength(), headerDecoder.version());
    }

    public void encode(long timestamp, long userId, long orderId, OrderStatus status, long lastPrice, long lastQty, long cumQty, long avgPrice, long clientOrderId) {
        encodeReport(timestamp, userId, orderId, status, lastPrice, lastQty, cumQty, avgPrice, clientOrderId);
    }
}
