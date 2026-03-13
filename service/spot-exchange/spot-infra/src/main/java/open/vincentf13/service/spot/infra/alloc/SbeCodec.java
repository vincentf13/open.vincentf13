package open.vincentf13.service.spot.infra.alloc;

import net.openhft.chronicle.bytes.PointerBytesStore;
import open.vincentf13.service.spot.sbe.*;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * SBE 高效能編解碼門面 (SBE Codec Facade)
 * <p>
 * 本類別採用「零物件分配 (Zero-Allocation)」設計，所有資源均從 {@link ThreadContext} 復用。
 * 統一使用 {@link PointerBytesStore} 作為解碼輸入，確保與 Model 載體無縫對接。
 */
public class SbeCodec {
    /** SBE 訊息頭長度 */
    public static final int HEADER_SIZE = 8;

    // --- 指令編碼 (Encoding) ---

    public static int encodeAuth(long timestamp, long userId) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        AuthEncoder encoder = ctx.getAuthEncoder();
        
        ctx.getHeaderEncoder().wrap(buffer, 0)
                .blockLength(AuthEncoder.BLOCK_LENGTH)
                .templateId(AuthEncoder.TEMPLATE_ID)
                .schemaId(AuthEncoder.SCHEMA_ID)
                .version(AuthEncoder.SCHEMA_VERSION);
        
        encoder.wrap(buffer, HEADER_SIZE)
                .timestamp(timestamp)
                .userId(userId);
        return HEADER_SIZE + encoder.encodedLength();
    }

    public static int encodeOrderCreate(long timestamp, long userId, int symbolId, long price, long qty, Side side, long clientOrderId) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        OrderCreateEncoder encoder = ctx.getOrderCreateEncoder();

        ctx.getHeaderEncoder().wrap(buffer, 0)
                .blockLength(OrderCreateEncoder.BLOCK_LENGTH)
                .templateId(OrderCreateEncoder.TEMPLATE_ID)
                .schemaId(OrderCreateEncoder.SCHEMA_ID)
                .version(OrderCreateEncoder.SCHEMA_VERSION);

        encoder.wrap(buffer, HEADER_SIZE)
               .timestamp(timestamp)
               .userId(userId)
               .symbolId(symbolId)
               .price(price)
               .qty(qty)
               .side(side)
               .clientOrderId(clientOrderId);
        return HEADER_SIZE + encoder.encodedLength();
    }

    public static int encodeOrderCancel(long timestamp, long userId, long orderId) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        OrderCancelEncoder encoder = ctx.getOrderCancelEncoder();

        ctx.getHeaderEncoder().wrap(buffer, 0)
                .blockLength(OrderCancelEncoder.BLOCK_LENGTH)
                .templateId(OrderCancelEncoder.TEMPLATE_ID)
                .schemaId(OrderCancelEncoder.SCHEMA_ID)
                .version(OrderCancelEncoder.SCHEMA_VERSION);

        encoder.wrap(buffer, HEADER_SIZE)
                .timestamp(timestamp)
                .userId(userId)
                .orderId(orderId);
        return HEADER_SIZE + encoder.encodedLength();
    }

    public static int encodeDeposit(long timestamp, long userId, int assetId, long amount) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        DepositEncoder encoder = ctx.getDepositEncoder();

        ctx.getHeaderEncoder().wrap(buffer, 0)
                .blockLength(DepositEncoder.BLOCK_LENGTH)
                .templateId(DepositEncoder.TEMPLATE_ID)
                .schemaId(DepositEncoder.SCHEMA_ID)
                .version(DepositEncoder.SCHEMA_VERSION);

        encoder.wrap(buffer, HEADER_SIZE)
                .timestamp(timestamp)
                .userId(userId)
                .assetId(assetId)
                .amount(amount);
        return HEADER_SIZE + encoder.encodedLength();
    }

    public static int encodeExecutionReport(long timestamp, long userId, long orderId, OrderStatus status, long lastPrice, long lastQty, long cumQty, long avgPrice, long clientOrderId) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        ExecutionReportEncoder encoder = ctx.getExecutionReportEncoder();

        ctx.getHeaderEncoder().wrap(buffer, 0)
                .blockLength(ExecutionReportEncoder.BLOCK_LENGTH)
                .templateId(ExecutionReportEncoder.TEMPLATE_ID)
                .schemaId(ExecutionReportEncoder.SCHEMA_ID)
                .version(ExecutionReportEncoder.SCHEMA_VERSION);

        encoder.wrap(buffer, HEADER_SIZE)
               .timestamp(timestamp)
               .userId(userId)
               .orderId(orderId)
               .status(status)
               .lastPrice(lastPrice)
               .lastQty(lastQty)
               .cumQty(cumQty)
               .avgPrice(avgPrice)
               .clientOrderId(clientOrderId);
        return HEADER_SIZE + encoder.encodedLength();
    }

    // --- 指令解碼 (Decoding) ---

    public static AuthDecoder decodeAuth(PointerBytesStore store) {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrap(store);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        header.wrap(buffer, 0);
        AuthDecoder decoder = ctx.getAuthDecoder();
        decoder.wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
        return decoder;
    }

    public static OrderCreateDecoder decodeOrderCreate(PointerBytesStore store) {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrap(store);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        header.wrap(buffer, 0);
        OrderCreateDecoder decoder = ctx.getOrderCreateDecoder();
        decoder.wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
        return decoder;
    }

    public static OrderCancelDecoder decodeOrderCancel(PointerBytesStore store) {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrap(store);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        header.wrap(buffer, 0);
        OrderCancelDecoder decoder = ctx.getOrderCancelDecoder();
        decoder.wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
        return decoder;
    }

    public static DepositDecoder decodeDeposit(PointerBytesStore store) {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrap(store);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        header.wrap(buffer, 0);
        DepositDecoder decoder = ctx.getDepositDecoder();
        decoder.wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
        return decoder;
    }

    public static ExecutionReportDecoder decodeExecutionReport(PointerBytesStore store) {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrap(store);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        header.wrap(buffer, 0);
        ExecutionReportDecoder decoder = ctx.getExecutionReportDecoder();
        decoder.wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
        return decoder;
    }

    /** 將 Chronicle 的 Pointer 轉換為 Agrona 的 DirectBuffer 視圖 */
    private static DirectBuffer wrap(PointerBytesStore store) {
        return ThreadContext.get().getScratchBuffer().wrap(store.addressForRead(0), (int) store.readRemaining());
    }
}
