package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
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
    private final OrderCancelEncoder encoder = new OrderCancelEncoder();
    private final OrderCancelDecoder decoder = new OrderCancelDecoder();

    public OrderCancelDecoder decode() {
        DirectBuffer buffer = wrapStore(pointBytesStore);
        headerDecoder.wrap(buffer, 0);
        return decoder.wrap(buffer, HEADER_SIZE, headerDecoder.blockLength(), headerDecoder.version());
    }

    public void encode(long timestamp, long userId, long orderId) {
        MutableDirectBuffer buffer = ThreadContext.get().getScratchBuffer().wrapForWrite();
        wrapHeader(buffer, OrderCancelEncoder.TEMPLATE_ID, OrderCancelEncoder.BLOCK_LENGTH, OrderCancelEncoder.SCHEMA_ID, OrderCancelEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(timestamp).userId(userId).orderId(orderId);
        fillFromScratch(HEADER_SIZE + encoder.encodedLength());
    }
}
