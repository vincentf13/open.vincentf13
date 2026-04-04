package open.vincentf13.service.spot.model.command;

import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.DirectBuffer;

/** ORDER_CREATE 指令解碼器 (Flyweight) */
public class OrderCreateCommand extends AbstractSbeModel {
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();

    @Override protected void decoderReWrap(DirectBuffer buffer, int offset, int blockLength, int version) {
        decoder.wrap(buffer, offset, blockLength, version);
    }

    public long getUserId() { return decoder.userId(); }
    public int getSymbolId() { return decoder.symbolId(); }
    public long getPrice() { return decoder.price(); }
    public long getQty() { return decoder.qty(); }
    public Side getSide() { return decoder.side(); }
    public long getClientOrderId() { return decoder.clientOrderId(); }
    public long getTimestamp() { return decoder.timestamp(); }
}
