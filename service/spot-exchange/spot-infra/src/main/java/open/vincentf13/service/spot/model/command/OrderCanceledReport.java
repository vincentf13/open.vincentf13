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

    public OrderCanceledReport write(MutableDirectBuffer dstBuffer, int offset) {
        this.buffer.wrap(dstBuffer, offset, encodedLength());
        return this;
    }

    public void set(long seq, long timestamp, long userId, long orderId, long filledQty, long clientOrderId) {
        fillCommonHeader(MsgType.ORDER_CANCELED, seq, OrderCanceledEncoder.TEMPLATE_ID, OrderCanceledEncoder.BLOCK_LENGTH, OrderCanceledEncoder.SCHEMA_ID, OrderCanceledEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, BODY_OFFSET).timestamp(timestamp).userId(userId).orderId(orderId).filledQty(filledQty).clientOrderId(clientOrderId);
        refreshDecoder();
    }

    @Override public int encodedLength() { return BODY_OFFSET + OrderCanceledEncoder.BLOCK_LENGTH; }
    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
    public long getOrderId() { return decoder.orderId(); }
    public OrderStatus getStatus() { return OrderStatus.CANCELED; }
    public long getClientOrderId() { return decoder.clientOrderId(); }
    public long getFilledQty() { return decoder.filledQty(); }
}
