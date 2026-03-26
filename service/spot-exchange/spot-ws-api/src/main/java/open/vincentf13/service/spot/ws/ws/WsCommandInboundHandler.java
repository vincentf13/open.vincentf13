package open.vincentf13.service.spot.ws.ws;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 * 網關 WebSocket 指令處理器 (極簡 Micrometer 版)
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WsCommandInboundHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {
    private final ChronicleQueue wal = Storage.self().gatewaySenderWal();
    private final WsSessionManager sessionManager;
    private final ThreadLocal<ExcerptAppender> appenderThreadLocal = ThreadLocal.withInitial(wal::acquireAppender);

    public WsCommandInboundHandler(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        final long arrivalTimeNs = System.nanoTime();
        StaticMetricsHolder.addCounter(MetricsKey.NETTY_RECV_COUNT, 1);

        io.netty.buffer.ByteBuf content = frame.content();
        int length = content.readableBytes();
        if (length < 20) return;

        content.setLongLE(content.readerIndex() + 12, arrivalTimeNs);

        if (ctx.channel().attr(WsSessionManager.USER_ID_KEY).get() == null) {
            int msgType = content.getIntLE(content.readerIndex());
            if (msgType == MsgType.AUTH) {
                long userId = content.getLongLE(content.readerIndex() + 28);
                sessionManager.addSession(userId, ctx.channel());
            }
        }

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
            StaticMetricsHolder.addCounter(MetricsKey.GATEWAY_WAL_WRITE_COUNT, 1);
        } catch (Exception e) {
            log.error("[GATEWAY-WS] WAL 直寫失敗: {}", e.getMessage());
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof BinaryWebSocketFrame) {
            super.channelRead(ctx, msg);
        } else {
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long uid = sessionManager.getUserIdByChannel(ctx.channel());
        if (uid != null) sessionManager.removeSession(uid, ctx.channel());
    }
}
