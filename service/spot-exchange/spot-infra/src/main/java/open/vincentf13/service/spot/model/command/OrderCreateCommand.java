package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.DirectBuffer;

/**
 * 下單指令 (實例私有編解碼器，無 ThreadLocal 依賴)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCreateCommand extends AbstractSbeModel {
    private final OrderCreateEncoder encoder = new OrderCreateEncoder();
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();

    public OrderCreateDecoder decode() {
        DirectBuffer buffer = wrapStore();
        headerDecoder.wrap(buffer, 0);
        return decoder.wrap(buffer, HEADER_SIZE, headerDecoder.blockLength(), headerDecoder.version());
    }

    public void encode(long timestamp, long userId, int symbolId, long price, long qty, Side side, long clientOrderId) {
        wrapHeader(OrderCreateEncoder.TEMPLATE_ID, OrderCreateEncoder.BLOCK_LENGTH, OrderCreateEncoder.SCHEMA_ID, OrderCreateEncoder.SCHEMA_VERSION);
        encoder.wrap(selfBuffer, HEADER_SIZE)
                .timestamp(timestamp)
                .userId(userId)
                .symbolId(symbolId)
                .price(price)
                .qty(qty)
                .side(side)
                .clientOrderId(clientOrderId);
        
        int totalLength = HEADER_SIZE + encoder.encodedLength();
        this.pointBytesStore.set(selfBuffer.addressOffset(), totalLength);
    }

    // --- 指令字段 Getter (透過 decoder) ---
    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
    public int getSymbolId() { return decoder.symbolId(); }
    public long getPrice() { return decoder.price(); }
    public long getQty() { return decoder.qty(); }
    public Side getSide() { return decoder.side(); }
    public long getClientOrderId() { return decoder.clientOrderId(); }
}
