package open.vincentf13.service.spot.ws.ws;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.bytes.PointerBytesStore;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.MetricsCollector;
import open.vincentf13.service.spot.model.command.*;
import open.vincentf13.service.spot.sbe.Side;
import open.vincentf13.service.spot.ws.util.JsonUtil;
import org.agrona.MutableDirectBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 網關 WebSocket 指令處理器 (極致直寫 WAL 版)
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WsCommandInboundHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private final ChronicleQueue wal = Storage.self().gatewaySenderWal();
    private final WsSessionManager sessionManager;
    
    // 核心優化：ThreadLocal Appender，消除 RingBuffer 競爭與一次內存拷貝
    private final ThreadLocal<ExcerptAppender> appenderThreadLocal = ThreadLocal.withInitial(wal::acquireAppender);
    private final ThreadLocal<PointerBytesStore> pointerThreadLocal = ThreadLocal.withInitial(PointerBytesStore::new);

    public WsCommandInboundHandler(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("[GATEWAY-WS] 檢測到新的 TCP 連線: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    // 指標更新頻率控制 (基於 ThreadLocal 以確保執行緒安全且無競爭)
    private final ThreadLocal<Long> localNettyRecvCount = ThreadLocal.withInitial(() -> 0L);
    private static final int METRICS_BATCH_SIZE = 5000;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {     
        handleTextMessage(ctx, frame.text());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame frame) {
            try {
                handleBinaryMessage(ctx, frame);
            } finally {
                io.netty.util.ReferenceCountUtil.release(frame);
            }
        } else {
            super.channelRead(ctx, msg);
        }
    }

    private void handleBinaryMessage(ChannelHandlerContext ctx, io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame frame) {
        updateNettyMetrics();

        io.netty.buffer.ByteBuf content = frame.content();
        int length = content.readableBytes();
        if (length < 20) { 
            log.warn("[GATEWAY-WS] 收到無效封包，長度不足: {}", length);
            return;
        }

        int msgType = content.getIntLE(content.readerIndex());
        
        if (msgType == open.vincentf13.service.spot.infra.Constants.MsgType.AUTH) {
            long userId = content.getLongLE(content.readerIndex() + 20 + 8);
            log.debug("[GATEWAY] 二進制 AUTH 識別: uid={}", userId);
            sessionManager.addSession(userId, ctx.channel());
        }

        // --- 核心優化：物理記憶體零拷貝直寫 WAL ---
        ExcerptAppender appender = appenderThreadLocal.get();
        PointerBytesStore pointer = pointerThreadLocal.get();
        
        try (var dc = appender.writingDocument()) {
            if (content.hasMemoryAddress()) {
                // 如果是直接記憶體，直接映射物理位址寫入，完全避開中間對象與額外拷貝
                pointer.set(content.memoryAddress() + content.readerIndex(), length);
                dc.wire().bytes().write(pointer);
            } else {
                // 備援方案：如果是 Heap Buffer，寫入 Bytes
                byte[] data = new byte[length];
                content.getBytes(content.readerIndex(), data);
                dc.wire().bytes().write(data);
            }
        } catch (Exception e) {
            log.error("[GATEWAY-WS] WAL 直寫失敗: {}", e.getMessage());
        }
    }

    private void handleTextMessage(ChannelHandlerContext ctx, String text) {
        updateNettyMetrics();

        log.debug("[GATEWAY-WS] 收到 JSON 訊息: {}", text); 
        final ThreadContext context = ThreadContext.get();
        final ThreadContext.RequestHolder holder = context.getRequestHolder();
        holder.reset();

        try {
            java.util.Map<String, Object> map = JsonUtil.toMap(text);
            holder.setOp((String) map.get("op"));
            if (map.get("uid") != null) holder.setUserId(((Number) map.get("uid")).longValue());
            if (map.get("oid") != null) holder.setOrderId(((Number) map.get("oid")).longValue());
            if (map.get("sid") != null) holder.setSymbolId(((Number) map.get("sid")).intValue());
            if (map.get("p") != null) holder.setPrice(((Number) map.get("p")).longValue());
            if (map.get("q") != null) holder.setQty(((Number) map.get("q")).longValue());
            holder.setSide((String) map.get("side"));
            if (map.get("cid") != null) holder.setCid(((Number) map.get("cid")).longValue());
            if (map.get("amt") != null) holder.setAmount(((Number) map.get("amt")).longValue());
            if (map.get("aid") != null) holder.setAssetId(((Number) map.get("aid")).intValue());
        } catch (Exception e) {
            log.warn("JSON 解析失敗: {}, raw: {}", e.getMessage(), text);
        }

        if (holder.getOp() == null) return;

        final MutableDirectBuffer scratch = context.getScratchBuffer().wrapForWrite();

        switch (holder.getOp()) {
            case "auth" -> {
                log.debug("[GATEWAY] 處理 AUTH: uid={}", holder.getUserId());
                sessionManager.addSession(holder.getUserId(), ctx.channel());
                AuthCommand cmd = context.getAuthCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, open.vincentf13.service.spot.infra.util.Clock.now(), holder.getUserId());
                directWrite(cmd);
            }
            case "order_create" -> {
                log.debug("[GATEWAY] 處理 ORDER_CREATE: uid={}, sid={}, p={}, q={}, side={}, cid={}", 
                    holder.getUserId(), holder.getSymbolId(), holder.getPrice(), holder.getQty(), holder.getSide(), holder.getCid());
                open.vincentf13.service.spot.sbe.Side side = "BUY".equalsIgnoreCase(holder.getSide()) ? open.vincentf13.service.spot.sbe.Side.BUY : open.vincentf13.service.spot.sbe.Side.SELL;
                OrderCreateCommand cmd = context.getOrderCreateCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, open.vincentf13.service.spot.infra.util.Clock.now(), holder.getUserId(), holder.getSymbolId(), holder.getPrice(), holder.getQty(), side, holder.getCid());
                directWrite(cmd);
            }
            case "order_cancel" -> {
                log.debug("[GATEWAY] 處理 ORDER_CANCEL: uid={}, oid={}", holder.getUserId(), holder.getOrderId());
                OrderCancelCommand cmd = context.getOrderCancelCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, open.vincentf13.service.spot.infra.util.Clock.now(), holder.getUserId(), holder.getOrderId());
                directWrite(cmd);
            }
            case "deposit" -> {
                log.debug("[GATEWAY] 處理 DEPOSIT: uid={}, aid={}, amt={}", holder.getUserId(), holder.getAssetId(), holder.getAmount());
                DepositCommand cmd = context.getDepositCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, open.vincentf13.service.spot.infra.util.Clock.now(), holder.getUserId(), holder.getAssetId(), holder.getAmount());
                directWrite(cmd);
            }
        }
    }

    private void updateNettyMetrics() {
        long count = localNettyRecvCount.get() + 1;
        if (count >= METRICS_BATCH_SIZE) {
            MetricsCollector.add(MetricsKey.NETTY_RECV_COUNT, count);
            localNettyRecvCount.set(0L);
        } else {
            localNettyRecvCount.set(count);
        }
    }

    private void directWrite(AbstractSbeModel model) {
        ExcerptAppender appender = appenderThreadLocal.get();
        PointerBytesStore pointer = pointerThreadLocal.get();
        try (var dc = appender.writingDocument()) {
            pointer.set(model.getUnsafeBuffer().addressOffset(), model.totalByteLength());
            dc.wire().bytes().write(pointer);
        }
        log.debug("[GATEWAY-WS] 訊息已直寫 WAL, type={}, len={}", model.getMsgType(), model.totalByteLength());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long uid = sessionManager.getUserIdByChannel(ctx.channel());
        if (uid != null) sessionManager.removeSession(uid, ctx.channel());
    }
}

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long uid = sessionManager.getUserIdByChannel(ctx.channel());
        if (uid != null) sessionManager.removeSession(uid, ctx.channel());
    }
}
