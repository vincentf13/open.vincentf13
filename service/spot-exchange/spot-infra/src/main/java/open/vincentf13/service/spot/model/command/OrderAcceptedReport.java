package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderAcceptedDecoder;
import open.vincentf13.service.spot.sbe.OrderAcceptedEncoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 訂單接受回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderAcceptedReport extends AbstractSbeModel {
    private final OrderAcceptedEncoder encoder = new OrderAcceptedEncoder();
    private final OrderAcceptedDecoder decoder = new OrderAcceptedDecoder();

    @Override protected void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version) { decoder.wrap(buffer, offset, blockLength, version); }

    public OrderAcceptedEncoder write(MutableDirectBuffer dstBuffer, int offset, long seq) {
        this.buffer.wrap(dstBuffer, offset, BODY_OFFSET + OrderAcceptedEncoder.BLOCK_LENGTH);
        preEncode(dstBuffer, offset, MsgType.ORDER_ACCEPTED, seq, OrderAcceptedEncoder.TEMPLATE_ID, OrderAcceptedEncoder.BLOCK_LENGTH, OrderAcceptedEncoder.SCHEMA_ID, OrderAcceptedEncoder.SCHEMA_VERSION);
        return encoder.wrap(dstBuffer, offset + BODY_OFFSET);
    }

    @Override public int encodedLength() { return BODY_OFFSET + OrderAcceptedEncoder.BLOCK_LENGTH; }
    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
    public long getOrderId() { return decoder.orderId(); }
    public OrderStatus getStatus() { return OrderStatus.NEW; }
    public long getClientOrderId() { return decoder.clientOrderId(); }
}
