package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.ExecutionReportDecoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.DirectBuffer;

/**
 * 訂單接受回報
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderAcceptedReport extends AbstractSbeModel {
    private final ExecutionReportDecoder decoder = new ExecutionReportDecoder();

    public ExecutionReportDecoder decode() {
        DirectBuffer buffer = wrapStore(pointBytesStore);
        headerDecoder.wrap(buffer, 0);
        return decoder.wrap(buffer, HEADER_SIZE, headerDecoder.blockLength(), headerDecoder.version());
    }

    public void encode(long timestamp, long userId, long orderId, long clientOrderId) {
        encodeReport(timestamp, userId, orderId, OrderStatus.NEW, 0, 0, 0, 0, clientOrderId);
    }
}
