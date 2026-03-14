package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderCancelDecoder;
import open.vincentf13.service.spot.sbe.OrderCancelEncoder;
import org.agrona.DirectBuffer;

/** 撤單指令 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCancelCommand extends AbstractSbeModel {
    private final OrderCancelEncoder encoder = new OrderCancelEncoder();
    private final OrderCancelDecoder decoder = new OrderCancelDecoder();

    @Override
    protected void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version) {
        decoder.wrap(buffer, offset, blockLength, version);
    }

    public void encode(long timestamp, long userId, long orderId) {
        encoder.wrap(preEncode(OrderCancelEncoder.TEMPLATE_ID, OrderCancelEncoder.BLOCK_LENGTH, OrderCancelEncoder.SCHEMA_ID, OrderCancelEncoder.SCHEMA_VERSION), HEADER_SIZE)
                .timestamp(timestamp).userId(userId).orderId(orderId);
        postEncode(encoder.encodedLength());
    }

    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
    public long getOrderId() { return decoder.orderId(); }
}
