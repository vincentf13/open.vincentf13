package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.sbe.MessageHeaderDecoder;
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
    public OrderCreateDecoder decode() {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrapStore(pointBytesStore);
        ctx.getHeaderDecoder().wrap(buffer, 0);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        return ctx.getOrderCreateDecoder().wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
    }

    public void encode(long timestamp, long userId, int symbolId, long price, long qty, Side side, long clientOrderId) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        OrderCreateEncoder encoder = ctx.getOrderCreateEncoder();
        wrapHeader(buffer, OrderCreateEncoder.TEMPLATE_ID, OrderCreateEncoder.BLOCK_LENGTH, OrderCreateEncoder.SCHEMA_ID, OrderCreateEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(timestamp).userId(userId).symbolId(symbolId).price(price).qty(qty).side(side).clientOrderId(clientOrderId);
        fillFromScratch(HEADER_SIZE + encoder.encodedLength());
    }
}
