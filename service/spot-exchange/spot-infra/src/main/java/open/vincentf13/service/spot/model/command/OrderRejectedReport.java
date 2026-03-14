package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.OrderRejectedDecoder;
import open.vincentf13.service.spot.sbe.OrderRejectedEncoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 訂單拒絕回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderRejectedReport extends AbstractSbeModel {
    private final OrderRejectedEncoder encoder = new OrderRejectedEncoder();
    private final OrderRejectedDecoder decoder = new OrderRejectedDecoder();

    @Override protected void decoderReWrap(DirectBuffer buffer, int offset, int blockLength, int version) { decoder.wrap(buffer, offset, blockLength, version); }

    public OrderRejectedReport wrapWriteBuffer(MutableDirectBuffer dstBuffer, int offset) {
        this.unsafeBuffer.wrap(dstBuffer, offset, totalByteLength());
        return this;
    }

    public void set(long seq, long timestamp, long userId, long clientOrderId) {
        fillHeader(MsgType.ORDER_REJECTED, seq, OrderRejectedEncoder.TEMPLATE_ID, OrderRejectedEncoder.BLOCK_LENGTH, OrderRejectedEncoder.SCHEMA_ID, OrderRejectedEncoder.SCHEMA_VERSION);
        encoder.wrap(unsafeBuffer, BODY_OFFSET).timestamp(timestamp).userId(userId).clientOrderId(clientOrderId);
        refreshDecoder();
    }

    @Override public int totalByteLength() { return BODY_OFFSET + OrderRejectedEncoder.BLOCK_LENGTH; }
    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
    public long getOrderId() { return 0L; }
    public OrderStatus getStatus() { return OrderStatus.REJECTED; }
    public long getClientOrderId() { return decoder.clientOrderId(); }
}
