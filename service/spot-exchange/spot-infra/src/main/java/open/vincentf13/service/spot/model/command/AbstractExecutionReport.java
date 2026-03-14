package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.ExecutionReportDecoder;
import open.vincentf13.service.spot.sbe.ExecutionReportEncoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import org.agrona.DirectBuffer;

/**
 * 執行回報抽象類 (歸併 Accepted, Rejected, Canceled, Match)
 * 封裝了 ExecutionReport 專屬的編解碼器與通用解碼字段訪問
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractExecutionReport extends AbstractSbeModel {
    protected final ExecutionReportEncoder encoder = new ExecutionReportEncoder();
    protected final ExecutionReportDecoder decoder = new ExecutionReportDecoder();

    @Override
    protected void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version) {
        decoder.wrap(buffer, offset, blockLength, version);
    }

    /** 執行回報專屬的編碼邏輯 */
    protected void encodeReport(long ts, long uid, long oid, OrderStatus st, long lp, long lq, long cq, long ap, long cid) {
        encoder.wrap(preEncode(ExecutionReportEncoder.TEMPLATE_ID, ExecutionReportEncoder.BLOCK_LENGTH, ExecutionReportEncoder.SCHEMA_ID, ExecutionReportEncoder.SCHEMA_VERSION), HEADER_SIZE)
                .timestamp(ts).userId(uid).orderId(oid).clientOrderId(cid)
                .status(st).lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap);
        postEncode(encoder.encodedLength());
    }

    // --- 統一字段 Getter ---
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
