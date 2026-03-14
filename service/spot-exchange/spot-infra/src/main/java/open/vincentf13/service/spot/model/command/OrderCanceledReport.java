package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderCanceledDecoder;
import open.vincentf13.service.spot.sbe.OrderCanceledEncoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 訂單撤單回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCanceledReport extends AbstractSbeModel {
    private final OrderCanceledEncoder encoder = new OrderCanceledEncoder();
    private final OrderCanceledDecoder decoder = new OrderCanceledDecoder();

    @Override protected void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version) { decoder.wrap(buffer, offset, blockLength, version); }

    public OrderCanceledEncoder write(MutableDirectBuffer dstBuffer, int offset, long seq) {
        this.buffer.wrap(dstBuffer, offset, BODY_OFFSET + OrderCanceledEncoder.BLOCK_LENGTH);
        preEncode(dstBuffer, offset, MsgType.ORDER_CANCELED, seq, OrderCanceledEncoder.TEMPLATE_ID, OrderCanceledEncoder.BLOCK_LENGTH, OrderCanceledEncoder.SCHEMA_ID, OrderCanceledEncoder.SCHEMA_VERSION);
        return encoder.wrap(dstBuffer, offset + BODY_OFFSET);
    }

    @Override public int encodedLength() { return BODY_OFFSET + OrderCanceledEncoder.BLOCK_LENGTH; }
    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
    public long getOrderId() { return decoder.orderId(); }
    public OrderStatus getStatus() { return OrderStatus.CANCELED; }
    public long getClientOrderId() { return decoder.clientOrderId(); }
    public long getCumQty() { return decoder.filledQty(); }
}
