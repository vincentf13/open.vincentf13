package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 
 * 統一格式下單指令
 * 佈局：[Type:4] + [Seq:8] + [SbeHeader:8] + [Body:...]
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCreateCommand extends AbstractSbeModel {
    private final OrderCreateEncoder encoder = new OrderCreateEncoder();
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();

    @Override protected void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version) { decoder.wrap(buffer, offset, blockLength, version); }

    public OrderCreateEncoder write(MutableDirectBuffer dstBuffer, int offset, long seq) {
        this.buffer.wrap(dstBuffer, offset, BODY_OFFSET + OrderCreateEncoder.BLOCK_LENGTH);
        preEncode(dstBuffer, offset, MsgType.ORDER_CREATE, seq, OrderCreateEncoder.TEMPLATE_ID, OrderCreateEncoder.BLOCK_LENGTH, OrderCreateEncoder.SCHEMA_ID, OrderCreateEncoder.SCHEMA_VERSION);
        return encoder.wrap(dstBuffer, offset + BODY_OFFSET);
    }

    @Override public int encodedLength() { return BODY_OFFSET + OrderCreateEncoder.BLOCK_LENGTH; }

    public long getUserId() { return decoder.userId(); }
    public int getSymbolId() { return decoder.symbolId(); }
    public long getPrice() { return decoder.price(); }
    public long getQty() { return decoder.qty(); }
    public Side getSide() { return decoder.side(); }
    public long getClientOrderId() { return decoder.clientOrderId(); }
    public long getTimestamp() { return decoder.timestamp(); }
}
