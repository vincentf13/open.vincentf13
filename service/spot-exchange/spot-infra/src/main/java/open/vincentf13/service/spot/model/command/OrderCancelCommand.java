package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.sbe.MessageHeaderDecoder;
import open.vincentf13.service.spot.sbe.OrderCancelDecoder;
import open.vincentf13.service.spot.sbe.OrderCancelEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * 撤單指令
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCancelCommand extends AbstractSbeModel {
    public OrderCancelDecoder decode() {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrapStore(pointBytesStore);
        ctx.getHeaderDecoder().wrap(buffer, 0);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        return ctx.getOrderCancelDecoder().wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
    }

    public void encode(long timestamp, long userId, long orderId) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        OrderCancelEncoder encoder = ctx.getOrderCancelEncoder();
        wrapHeader(buffer, OrderCancelEncoder.TEMPLATE_ID, OrderCancelEncoder.BLOCK_LENGTH, OrderCancelEncoder.SCHEMA_ID, OrderCancelEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(timestamp).userId(userId).orderId(orderId);
        fillFromScratch(HEADER_SIZE + encoder.encodedLength());
    }
}
