package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.sbe.ExecutionReportDecoder;
import open.vincentf13.service.spot.sbe.MessageHeaderDecoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.DirectBuffer;

/**
 * 訂單接受回報
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderAcceptedReport extends AbstractSbeModel {
    public ExecutionReportDecoder decode() {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrapStore(pointBytesStore);
        ctx.getHeaderDecoder().wrap(buffer, 0);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        return ctx.getExecutionReportDecoder().wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
    }

    public void encode(long timestamp, long userId, long orderId, long clientOrderId) {
        encodeReport(timestamp, userId, orderId, OrderStatus.NEW, 0, 0, 0, 0, clientOrderId);
    }
}
