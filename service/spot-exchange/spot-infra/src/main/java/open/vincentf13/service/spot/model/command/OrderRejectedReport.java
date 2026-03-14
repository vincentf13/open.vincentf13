package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderRejectedDecoder;
import open.vincentf13.service.spot.sbe.OrderRejectedEncoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 訂單拒絕回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderRejectedReport extends AbstractSbeModel {
    private final OrderRejectedEncoder encoder = new OrderRejectedEncoder();
    private final OrderRejectedDecoder decoder = new OrderRejectedDecoder();

    @Override protected void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version) { decoder.wrap(buffer, offset, blockLength, version); }

    public OrderRejectedEncoder write(MutableDirectBuffer dstBuffer, int offset, long seq) {
        this.buffer.wrap(dstBuffer, offset, BODY_OFFSET + OrderRejectedEncoder.BLOCK_LENGTH);
        preEncode(dstBuffer, offset, MsgType.ORDER_REJECTED, seq, OrderRejectedEncoder.TEMPLATE_ID, OrderRejectedEncoder.BLOCK_LENGTH, OrderRejectedEncoder.SCHEMA_ID, OrderRejectedEncoder.SCHEMA_VERSION);
        return encoder.wrap(dstBuffer, offset + BODY_OFFSET);
    }

    @Override public int encodedLength() { return BODY_OFFSET + OrderRejectedEncoder.BLOCK_LENGTH; }
    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
    public long getOrderId() { return 0; }
    public OrderStatus getStatus() { return OrderStatus.REJECTED; }
    public long getClientOrderId() { return decoder.clientOrderId(); }
}
