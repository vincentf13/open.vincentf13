package open.vincentf13.service.spot.ws.ws;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.CounterMetrics;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 * 網關 WebSocket 指令處理器 (純二進制極簡版)
 * 職責：僅處理二進制 SBE 訊息，直寫 WAL，完全移除 JSON 支援
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WsCommandInboundHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {
    private final ChronicleQueue wal = Storage.self().gatewaySenderWal();
    private final WsSessionManager sessionManager;
    
    // 核心優化：ThreadLocal Appender，消除寫入爭用
    private final ThreadLocal<ExcerptAppender> appenderThreadLocal = ThreadLocal.withInitial(wal::acquireAppender);
    private final ThreadLocal<Long> localNettyRecvCount = ThreadLocal.withInitial(() -> 0L);
    private static final int METRICS_BATCH_SIZE = 100;

    public WsCommandInboundHandler(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("[GATEWAY-WS] 檢測到新的 TCP 連線: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        final long arrivalTimeNs = System.nanoTime();
        updateNettyMetrics();

        io.netty.buffer.ByteBuf content = frame.content();
        int length = content.readableBytes();
        if (length < 20) return;

        // 盡早填入網關接收時間戳
        content.setLongLE(content.readerIndex() + 12, arrivalTimeNs);

        // 1. 提取 MsgType 與處理 Session 綁定 (僅在尚未綁定時執行)
        if (ctx.channel().attr(WsSessionManager.USER_ID_KEY).get() == null) {
            int msgType = content.getIntLE(content.readerIndex());
            if (msgType == MsgType.AUTH) {
                long userId = content.getLongLE(content.readerIndex() + 28);
                sessionManager.addSession(userId, ctx.channel());
            }
        }

        // 2. --- 核心優化：物理記憶體零拷貝直寫 WAL ---
        final ThreadContext context = ThreadContext.get();
        final ExcerptAppender appender = appenderThreadLocal.get();
        final PointerBytesStore pointer = context.getReusablePointer();
        
        try (var dc = appender.writingDocument()) {
            if (content.hasMemoryAddress()) {
                pointer.set(content.memoryAddress() + content.readerIndex(), length);
                dc.wire().bytes().write(pointer);
            } else {
                byte[] scratch = new byte[length]; 
                content.getBytes(content.readerIndex(), scratch);
                dc.wire().bytes().write(scratch);
            }
            CounterMetrics.increment(MetricsKey.GATEWAY_WAL_WRITE_COUNT);
        } catch (Exception e) {
            log.error("[GATEWAY-WS] WAL 直寫失敗: {}", e.getMessage());
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof BinaryWebSocketFrame) {
            super.channelRead(ctx, msg);
        } else if (msg instanceof TextWebSocketFrame textFrame) {
            log.warn("[GATEWAY-WS] 已停用 JSON 支持，忽略訊息: {}", textFrame.text());
            textFrame.release();
        } else if (msg instanceof FullHttpRequest request) {
            log.warn("[GATEWAY-WS] 收到非 WebSocket 請求 (Path: {}), 正在釋放並關閉連線", request.uri());
            request.release();
            ctx.close();
        } else {
            // 處理其餘 WebSocket 框架 (如 Continuation) 與 Netty 內部消息
            // SimpleChannelInboundHandler.channelRead 會根據是否匹配泛型來決定是調用 channelRead0 還是 ctx.fireChannelRead
            try {
                super.channelRead(ctx, msg);
            } catch (Exception e) {
                ReferenceCountUtil.safeRelease(msg);
                throw e;
            }
        }
    }

    private void updateNettyMetrics() {
        long count = localNettyRecvCount.get() + 1;
        if (count >= METRICS_BATCH_SIZE) {
            CounterMetrics.add(MetricsKey.NETTY_RECV_COUNT, count);
            localNettyRecvCount.set(0L);
        } else {
            localNettyRecvCount.set(count);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long uid = sessionManager.getUserIdByChannel(ctx.channel());
        if (uid != null) sessionManager.removeSession(uid, ctx.channel());
    }
}
