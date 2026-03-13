package open.vincentf13.service.spot.infra.alloc;

import net.openhft.chronicle.bytes.PointerBytesStore;
import open.vincentf13.service.spot.sbe.*;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * SBE 高效能編解碼門面 (SBE Codec Facade)
 */
public class SbeCodec {
    public static final int HEADER_SIZE = 8;

    // --- 指令編碼 (Encoding) ---

    public static int encodeAuth(long timestamp, long userId) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        AuthEncoder encoder = ctx.getAuthEncoder();
        wrapHeader(buffer, AuthEncoder.TEMPLATE_ID, AuthEncoder.BLOCK_LENGTH, AuthEncoder.SCHEMA_ID, AuthEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(timestamp).userId(userId);
        return HEADER_SIZE + encoder.encodedLength();
    }

    public static int encodeOrderCreate(long timestamp, long userId, int symbolId, long price, long qty, Side side, long clientOrderId) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        OrderCreateEncoder encoder = ctx.getOrderCreateEncoder();
        wrapHeader(buffer, OrderCreateEncoder.TEMPLATE_ID, OrderCreateEncoder.BLOCK_LENGTH, OrderCreateEncoder.SCHEMA_ID, OrderCreateEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(timestamp).userId(userId).symbolId(symbolId).price(price).qty(qty).side(side).clientOrderId(clientOrderId);
        return HEADER_SIZE + encoder.encodedLength();
    }

    public static int encodeOrderCancel(long timestamp, long userId, long orderId) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        OrderCancelEncoder encoder = ctx.getOrderCancelEncoder();
        wrapHeader(buffer, OrderCancelEncoder.TEMPLATE_ID, OrderCancelEncoder.BLOCK_LENGTH, OrderCancelEncoder.SCHEMA_ID, OrderCancelEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(timestamp).userId(userId).orderId(orderId);
        return HEADER_SIZE + encoder.encodedLength();
    }

    public static int encodeDeposit(long timestamp, long userId, int assetId, long amount) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        DepositEncoder encoder = ctx.getDepositEncoder();
        wrapHeader(buffer, DepositEncoder.TEMPLATE_ID, DepositEncoder.BLOCK_LENGTH, DepositEncoder.SCHEMA_ID, DepositEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(timestamp).userId(userId).assetId(assetId).amount(amount);
        return HEADER_SIZE + encoder.encodedLength();
    }

    // --- 執行回報編碼 (Execution Report Specialized API) ---

    public static int encodeAcceptedReport(long timestamp, long userId, long orderId, long clientOrderId) {
        return encodeReport(timestamp, userId, orderId, OrderStatus.NEW, 0, 0, 0, 0, clientOrderId);
    }

    public static int encodeRejectedReport(long timestamp, long userId, long clientOrderId) {
        return encodeReport(timestamp, userId, 0, OrderStatus.REJECTED, 0, 0, 0, 0, clientOrderId);
    }

    public static int encodeCanceledReport(long timestamp, long userId, long orderId, long filledQuantity, long clientOrderId) {
        return encodeReport(timestamp, userId, orderId, OrderStatus.CANCELED, 0, 0, filledQuantity, 0, clientOrderId);
    }

    public static int encodeMatchedReport(long timestamp, long userId, long orderId, OrderStatus status, long lastPrice, long lastQty, long cumQty, long avgPrice, long clientOrderId) {
        return encodeReport(timestamp, userId, orderId, status, lastPrice, lastQty, cumQty, avgPrice, clientOrderId);
    }

    private static int encodeReport(long ts, long uid, long oid, OrderStatus st, long lp, long lq, long cq, long ap, long cid) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        ExecutionReportEncoder encoder = ctx.getExecutionReportEncoder();
        wrapHeader(buffer, ExecutionReportEncoder.TEMPLATE_ID, ExecutionReportEncoder.BLOCK_LENGTH, ExecutionReportEncoder.SCHEMA_ID, ExecutionReportEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(ts).userId(uid).orderId(oid).status(st).lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap).clientOrderId(cid);
        return HEADER_SIZE + encoder.encodedLength();
    }

    // --- 通用工具 ---

    private static void wrapHeader(MutableDirectBuffer buffer, int tid, int bl, int sid, int ver) {
        ThreadContext.get().getHeaderEncoder().wrap(buffer, 0).templateId(tid).blockLength(bl).schemaId(sid).version(ver);
    }

    public static AuthDecoder decodeAuth(PointerBytesStore store) {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrap(store);
        ctx.getHeaderDecoder().wrap(buffer, 0);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        return ctx.getAuthDecoder().wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
    }

    public static OrderCreateDecoder decodeOrderCreate(PointerBytesStore store) {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrap(store);
        ctx.getHeaderDecoder().wrap(buffer, 0);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        return ctx.getOrderCreateDecoder().wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
    }

    public static OrderCancelDecoder decodeOrderCancel(PointerBytesStore store) {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrap(store);
        ctx.getHeaderDecoder().wrap(buffer, 0);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        return ctx.getOrderCancelDecoder().wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
    }

    public static DepositDecoder decodeDeposit(PointerBytesStore store) {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrap(store);
        ctx.getHeaderDecoder().wrap(buffer, 0);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        return ctx.getDepositDecoder().wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
    }

    public static ExecutionReportDecoder decodeExecutionReport(PointerBytesStore store) {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrap(store);
        ctx.getHeaderDecoder().wrap(buffer, 0);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        return ctx.getExecutionReportDecoder().wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
    }

    private static DirectBuffer wrap(PointerBytesStore store) {
        return ThreadContext.get().getScratchBuffer().wrap(store.addressForRead(0), (int) store.readRemaining());
    }
}
