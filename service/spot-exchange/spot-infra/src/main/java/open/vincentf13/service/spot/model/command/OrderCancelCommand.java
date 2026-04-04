package open.vincentf13.service.spot.model.command;

import open.vincentf13.service.spot.sbe.OrderCancelDecoder;
import org.agrona.DirectBuffer;

/** ORDER_CANCEL 指令解碼器 (Flyweight) */
public class OrderCancelCommand extends AbstractSbeModel {
    private final OrderCancelDecoder decoder = new OrderCancelDecoder();

    @Override protected void decoderReWrap(DirectBuffer buffer, int offset, int blockLength, int version) {
        decoder.wrap(buffer, offset, blockLength, version);
    }

    public long getUserId() { return decoder.userId(); }
    public long getOrderId() { return decoder.orderId(); }
    public long getTimestamp() { return decoder.timestamp(); }
}
