package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderCancelDecoder;
import open.vincentf13.service.spot.sbe.OrderCancelEncoder;
import org.agrona.DirectBuffer;

/**
 * 撤單指令 (實例私有編解碼器)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCancelCommand extends AbstractSbeModel {
    private final OrderCancelEncoder encoder = new OrderCancelEncoder();
    private final OrderCancelDecoder decoder = new OrderCancelDecoder();

    public OrderCancelDecoder decode() {
        DirectBuffer buffer = wrapStore();
        headerDecoder.wrap(buffer, 0);
        return decoder.wrap(buffer, HEADER_SIZE, headerDecoder.blockLength(), headerDecoder.version());
    }

    public void encode(long timestamp, long userId, long orderId) {
        wrapHeader(OrderCancelEncoder.TEMPLATE_ID, OrderCancelEncoder.BLOCK_LENGTH, OrderCancelEncoder.SCHEMA_ID, OrderCancelEncoder.SCHEMA_VERSION);
        encoder.wrap(selfBuffer, HEADER_SIZE).timestamp(timestamp).userId(userId).orderId(orderId);
        int totalLength = HEADER_SIZE + encoder.encodedLength();
        this.pointBytesStore.set(selfBuffer.addressOffset(), totalLength);
    }

    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
    public long getOrderId() { return decoder.orderId(); }
}
