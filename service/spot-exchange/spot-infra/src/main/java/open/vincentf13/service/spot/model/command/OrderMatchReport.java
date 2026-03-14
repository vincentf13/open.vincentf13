package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderMatchedDecoder;
import open.vincentf13.service.spot.sbe.OrderMatchedEncoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 訂單成交回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderMatchReport extends AbstractSbeModel {
    private final OrderMatchedEncoder encoder = new OrderMatchedEncoder();
    private final OrderMatchedDecoder decoder = new OrderMatchedDecoder();

    @Override protected void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version) { decoder.wrap(buffer, offset, blockLength, version); }

    public OrderMatchedEncoder write(MutableDirectBuffer dstBuffer, int offset, long seq) {
        this.buffer.wrap(dstBuffer, offset, BODY_OFFSET + OrderMatchedEncoder.BLOCK_LENGTH);
        preEncode(dstBuffer, offset, MsgType.ORDER_MATCHED, seq, OrderMatchedEncoder.TEMPLATE_ID, OrderMatchedEncoder.BLOCK_LENGTH, OrderMatchedEncoder.SCHEMA_ID, OrderMatchedEncoder.SCHEMA_VERSION);
        return encoder.wrap(dstBuffer, offset + BODY_OFFSET);
    }

    @Override public int encodedLength() { return BODY_OFFSET + OrderMatchedEncoder.BLOCK_LENGTH; }
    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
    public long getOrderId() { return decoder.orderId(); }
    public OrderStatus getStatus() { return decoder.status(); }
    public long getClientOrderId() { return decoder.clientOrderId(); }
    public long getLastPrice() { return decoder.lastPrice(); }
    public long getLastQty() { return decoder.lastQty(); }
    public long getCumQty() { return decoder.cumQty(); }
    public long getAvgPrice() { return decoder.avgPrice(); }
}
