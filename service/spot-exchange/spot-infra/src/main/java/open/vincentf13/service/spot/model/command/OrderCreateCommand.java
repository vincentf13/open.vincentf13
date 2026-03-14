package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 統一格式下單指令 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCreateCommand extends AbstractSbeModel {
    private final OrderCreateEncoder encoder = new OrderCreateEncoder();
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();

    @Override protected void decoderReWrap(DirectBuffer buffer, int offset, int blockLength, int version) { decoder.wrap(buffer, offset, blockLength, version); }

    public OrderCreateCommand wrapWriteBuffer(MutableDirectBuffer dstBuffer, int offset) {
        this.unsafeBuffer.wrap(dstBuffer, offset, totalByteLength());
        return this;
    }

    public void set(long seq, long timestamp, long userId, int symbolId, long price, long qty, Side side, long clientOrderId) {
        fillHeader(MsgType.ORDER_CREATE, seq, OrderCreateEncoder.TEMPLATE_ID, OrderCreateEncoder.BLOCK_LENGTH, OrderCreateEncoder.SCHEMA_ID, OrderCreateEncoder.SCHEMA_VERSION);
        encoder.wrap(unsafeBuffer, BODY_OFFSET)
                .timestamp(timestamp).userId(userId).symbolId(symbolId).price(price).qty(qty).side(side).clientOrderId(clientOrderId);       
        refreshDecoder();
    }

    @Override public int totalByteLength() { return BODY_OFFSET + OrderCreateEncoder.BLOCK_LENGTH; }

    public long getUserId() { return decoder.userId(); }
    public int getSymbolId() { return decoder.symbolId(); }
    public long getPrice() { return decoder.price(); }
    public long getQty() { return decoder.qty(); }
    public Side getSide() { return decoder.side(); }
    public long getClientOrderId() { return decoder.clientOrderId(); }
    public long getTimestamp() { return decoder.timestamp(); }
}
