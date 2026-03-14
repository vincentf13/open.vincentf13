package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderCancelDecoder;
import open.vincentf13.service.spot.sbe.OrderCancelEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 統一格式撤單指令 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCancelCommand extends AbstractSbeModel {
    private final OrderCancelEncoder encoder = new OrderCancelEncoder();
    private final OrderCancelDecoder decoder = new OrderCancelDecoder();

    @Override protected void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version) { decoder.wrap(buffer, offset, blockLength, version); }

    public OrderCancelEncoder write(MutableDirectBuffer dstBuffer, int offset, long seq) {
        this.buffer.wrap(dstBuffer, offset, BODY_OFFSET + OrderCancelEncoder.BLOCK_LENGTH);
        preEncode(dstBuffer, offset, MsgType.ORDER_CANCEL, seq, OrderCancelEncoder.TEMPLATE_ID, OrderCancelEncoder.BLOCK_LENGTH, OrderCancelEncoder.SCHEMA_ID, OrderCancelEncoder.SCHEMA_VERSION);
        return encoder.wrap(dstBuffer, offset + BODY_OFFSET);
    }

    @Override public int encodedLength() { return BODY_OFFSET + OrderCancelEncoder.BLOCK_LENGTH; }
    public long getUserId() { return decoder.userId(); }
    public long getOrderId() { return decoder.orderId(); }
    public long getTimestamp() { return decoder.timestamp(); }
}
