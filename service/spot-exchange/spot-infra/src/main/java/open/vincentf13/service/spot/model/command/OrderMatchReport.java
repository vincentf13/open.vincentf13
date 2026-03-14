package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.ExecutionReportDecoder;
import open.vincentf13.service.spot.sbe.ExecutionReportEncoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * 訂單成交回報
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderMatchReport extends AbstractSbeModel {
    private final ExecutionReportEncoder encoder = new ExecutionReportEncoder();
    private final ExecutionReportDecoder decoder = new ExecutionReportDecoder();

    public ExecutionReportDecoder decode() {
        DirectBuffer buffer = wrapStore(pointBytesStore);
        headerDecoder.wrap(buffer, 0);
        return decoder.wrap(buffer, HEADER_SIZE, headerDecoder.blockLength(), headerDecoder.version());
    }

    public void encode(long timestamp, long userId, long orderId, OrderStatus status, long lastPrice, long lastQty, long cumQty, long avgPrice, long clientOrderId) {
        encodeReport(timestamp, userId, orderId, status, lastPrice, lastQty, cumQty, avgPrice, clientOrderId);
    }

    private void encodeReport(long timestamp, long userId, long orderId, OrderStatus status, long lastPrice, long lastQty, long cumQty, long avgPrice, long clientOrderId) {
        MutableDirectBuffer buffer = encodeBuffer.wrapForWrite();
        wrapHeader(buffer, ExecutionReportEncoder.TEMPLATE_ID, ExecutionReportEncoder.BLOCK_LENGTH, ExecutionReportEncoder.SCHEMA_ID, ExecutionReportEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(timestamp).userId(userId).orderId(orderId).status(status).lastPrice(lastPrice).lastQty(lastQty).cumQty(cumQty).avgPrice(avgPrice).clientOrderId(clientOrderId);
        fillFromEncodeBuffer(HEADER_SIZE + encoder.encodedLength());
    }
}
