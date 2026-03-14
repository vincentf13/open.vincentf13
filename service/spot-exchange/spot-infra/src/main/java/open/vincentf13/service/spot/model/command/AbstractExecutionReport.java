package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.ExecutionReportDecoder;
import open.vincentf13.service.spot.sbe.ExecutionReportEncoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/** 統一格式執行回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractExecutionReport extends AbstractSbeModel {
    protected final ExecutionReportEncoder encoder = new ExecutionReportEncoder();
    protected final ExecutionReportDecoder decoder = new ExecutionReportDecoder();

    @Override protected void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version) { decoder.wrap(buffer, offset, blockLength, version); }

    /** 直接在目標 Buffer 寫入回報 */
    protected void encodeReport(MutableDirectBuffer dstBuffer, int offset, int msgType, long seq, long ts, long uid, long oid, OrderStatus st, long lp, long lq, long cq, long ap, long cid) {
        this.buffer.wrap(dstBuffer, offset, BODY_OFFSET + ExecutionReportEncoder.BLOCK_LENGTH);
        preEncode(dstBuffer, offset, msgType, seq, ExecutionReportEncoder.TEMPLATE_ID, ExecutionReportEncoder.BLOCK_LENGTH, ExecutionReportEncoder.SCHEMA_ID, ExecutionReportEncoder.SCHEMA_VERSION);
        encoder.wrap(dstBuffer, offset + BODY_OFFSET)
                .timestamp(ts).userId(uid).orderId(oid).clientOrderId(cid)
                .status(st).lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap);
    }

    @Override public int encodedLength() { return BODY_OFFSET + ExecutionReportEncoder.BLOCK_LENGTH; }

    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
    public long getOrderId() { return decoder.orderId(); }
    public OrderStatus getStatus() { return decoder.status(); }
    public long getLastPrice() { return decoder.lastPrice(); }
    public long getLastQty() { return decoder.lastQty(); }
    public long getCumQty() { return decoder.cumQty(); }
    public long getAvgPrice() { return decoder.avgPrice(); }
    public long getClientOrderId() { return decoder.clientOrderId(); }
}
