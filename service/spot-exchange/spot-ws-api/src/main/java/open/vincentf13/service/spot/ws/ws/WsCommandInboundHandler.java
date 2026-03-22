package open.vincentf13.service.spot.ws.ws;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.MetricsCollector;
import open.vincentf13.service.spot.model.command.*;
import open.vincentf13.service.spot.sbe.Side;
import open.vincentf13.service.spot.ws.util.JsonUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 網關 WebSocket 指令處理器 (TPS 性能極限版)
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WsCommandInboundHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private final ManyToOneRingBuffer gatewayWalQueue = Storage.self().gatewayWalQueue();
    private final WsSessionManager sessionManager;

    public WsCommandInboundHandler(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("[GATEWAY-WS] 檢測到新的 TCP 連線: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    private long localNettyRecvCount = 0;
    private static final int METRICS_BATCH_SIZE = 10000;

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
        // 增加 Netty 接收指標 (批量)
        updateNettyMetrics();

        io.netty.buffer.ByteBuf content = frame.content();
        int length = content.readableBytes();
        if (length < 20) { // 至少要包含 Header
            log.warn("[GATEWAY-WS] 收到無效封包，長度不足: {}", length);
            return;
        }

        // 提取 MsgType (位於 AbstractSbeModel.TYPE_OFFSET = 0) - SBE 固定 Little Endian
        int msgType = content.getIntLE(content.readerIndex());
        
        // --- 關鍵修復：如果是 AUTH 指令，必須在網關層建立 Session ---
        if (msgType == open.vincentf13.service.spot.infra.Constants.MsgType.AUTH) {
            // Auth Body 位於 BODY_OFFSET = 20，其中 UserId 位於 Body 偏移 8 的位置
            // Layout: [Header 20][Timestamp 8][UserId 8]
            long userId = content.getLongLE(content.readerIndex() + 20 + 8);
            log.debug("[GATEWAY] 二進制 AUTH 識別: uid={}", userId);
            sessionManager.addSession(userId, ctx.channel());
        }

        // 直接將二進制數據推入 RingBuffer (極致性能：物理記憶體零拷貝轉發)
        int index = gatewayWalQueue.tryClaim(msgType, length);
        if (index > 0) {
            try {
                // 如果是直接記憶體，使用 Unsafe 進行物理拷貝，避開所有中間對象
                if (content.hasMemoryAddress()) {
                    org.agrona.UnsafeAccess.UNSAFE.copyMemory(
                        content.memoryAddress() + content.readerIndex(),
                        gatewayWalQueue.buffer().addressOffset() + index,
                        length
                    );
                } else {
                    // 備援方案：如果是 Heap Buffer，使用標準的 ByteBuffer 拷貝
                    java.nio.ByteBuffer dst = gatewayWalQueue.buffer().byteBuffer();
                    dst.clear().position(index).limit(index + length);
                    content.getBytes(content.readerIndex(), dst);
                }
            } catch (Exception e) {
                log.error("[GATEWAY-WS] 零拷貝轉發失敗: {}", e.getMessage());
            } finally {
                gatewayWalQueue.commit(index);
            }
        } else {
            log.warn("[GATEWAY-WS] RingBuffer 滿了！二進制訊息被丟棄");
        }
    }

    private void handleTextMessage(ChannelHandlerContext ctx, String text) {
        // 增加 Netty 接收指標 (批量)
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
                cmd.set(MSG_SEQ_NONE, System.currentTimeMillis(), holder.getUserId());
                asyncWrite(cmd);
            }
            case "order_create" -> {
                log.debug("[GATEWAY] 處理 ORDER_CREATE: uid={}, sid={}, p={}, q={}, side={}, cid={}", 
                    holder.getUserId(), holder.getSymbolId(), holder.getPrice(), holder.getQty(), holder.getSide(), holder.getCid());
                open.vincentf13.service.spot.sbe.Side side = "BUY".equalsIgnoreCase(holder.getSide()) ? open.vincentf13.service.spot.sbe.Side.BUY : open.vincentf13.service.spot.sbe.Side.SELL;
                OrderCreateCommand cmd = context.getOrderCreateCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, System.currentTimeMillis(), holder.getUserId(), holder.getSymbolId(), holder.getPrice(), holder.getQty(), side, holder.getCid());
                asyncWrite(cmd);
            }
            case "order_cancel" -> {
                log.debug("[GATEWAY] 處理 ORDER_CANCEL: uid={}, oid={}", holder.getUserId(), holder.getOrderId());
                OrderCancelCommand cmd = context.getOrderCancelCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, System.currentTimeMillis(), holder.getUserId(), holder.getOrderId());
                asyncWrite(cmd);
            }
            case "deposit" -> {
                log.debug("[GATEWAY] 處理 DEPOSIT: uid={}, aid={}, amt={}", holder.getUserId(), holder.getAssetId(), holder.getAmount());
                DepositCommand cmd = context.getDepositCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, System.currentTimeMillis(), holder.getUserId(), holder.getAssetId(), holder.getAmount());
                asyncWrite(cmd);
            }
        }
    }

    private void updateNettyMetrics() {
        localNettyRecvCount++;
        if (localNettyRecvCount >= METRICS_BATCH_SIZE) {
            MetricsCollector.add(MetricsKey.NETTY_RECV_COUNT, localNettyRecvCount);
            localNettyRecvCount = 0;
        }
    }

    private void asyncWrite(AbstractSbeModel model) {
        int length = model.totalByteLength();
        int msgType = model.getMsgType();
        int index = gatewayWalQueue.tryClaim(msgType, length);
        if (index > 0) {
            try {
                gatewayWalQueue.buffer().putBytes(index, model.getUnsafeBuffer(), 0, length);
            } finally {
                gatewayWalQueue.commit(index);
            }
            log.debug("[GATEWAY-WS] 訊息已推入 RingBuffer 佇列, type={}, len={}", msgType, length);
        } else {
            log.warn("[GATEWAY-WS] RingBuffer 滿了！訊息被丟棄，請增加 WAL_RING_BUFFER_SIZE");
        }
    }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long uid = sessionManager.getUserIdByChannel(ctx.channel());
        if (uid != null) sessionManager.removeSession(uid, ctx.channel());
    }
}
