package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * 下單指令
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCreateCommand extends AbstractSbeModel {
    private final OrderCreateEncoder encoder = new OrderCreateEncoder();
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();

    public OrderCreateDecoder decode() {
        DirectBuffer buffer = wrapStore(pointBytesStore);
        headerDecoder.wrap(buffer, 0);
        return decoder.wrap(buffer, HEADER_SIZE, headerDecoder.blockLength(), headerDecoder.version());
    }

    public void encode(long timestamp, long userId, int symbolId, long price, long qty, Side side, long clientOrderId) {
        MutableDirectBuffer buffer = ThreadContext.get().getScratchBuffer().wrapForWrite();
        wrapHeader(buffer, OrderCreateEncoder.TEMPLATE_ID, OrderCreateEncoder.BLOCK_LENGTH, OrderCreateEncoder.SCHEMA_ID, OrderCreateEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(timestamp).userId(userId).symbolId(symbolId).price(price).qty(qty).side(side).clientOrderId(clientOrderId);
        fillFromScratch(HEADER_SIZE + encoder.encodedLength());
    }
}
