package open.vincentf13.service.spot.ws.ws;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
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
    private static final int MIN_COMMAND_LENGTH = 32;
    private static final int MIN_AUTH_LENGTH = 48;

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
        if (length < MIN_COMMAND_LENGTH) return;

        content.setLongLE(content.readerIndex() + 16, arrivalTimeNs);
        Long authenticatedUserId = extractAuthUserId(ctx, content, length);

        final ThreadContext context = ThreadContext.get();
        final ExcerptAppender appender = appenderThreadLocal.get();
        final PointerBytesStore pointer = context.getReusablePointer();
        
        try (var dc = appender.writingDocument()) {
            writeFrameToWal(content, length, pointer, dc.wire().bytes());
            StaticMetricsHolder.addCounter(MetricsKey.GATEWAY_WAL_WRITE_COUNT, 1);
            if (authenticatedUserId != null) sessionManager.addSession(authenticatedUserId, ctx.channel());
        } catch (Exception e) {
            log.error("[GATEWAY-WS] WAL 直寫失敗", e);
        }
    }

    private Long extractAuthUserId(ChannelHandlerContext ctx, io.netty.buffer.ByteBuf content, int length) {
        if (ctx.channel().attr(WsSessionManager.USER_ID_KEY).get() != null) return null;
        if (length < MIN_AUTH_LENGTH) return null;

        int readerIndex = content.readerIndex();
        if (content.getIntLE(readerIndex) != MsgType.AUTH) return null;

        long userId = content.getLongLE(readerIndex + 40);
        return userId > 0 ? userId : null;
    }

    private void writeFrameToWal(io.netty.buffer.ByteBuf content, int length, PointerBytesStore pointer, net.openhft.chronicle.bytes.Bytes<?> targetBytes) {
        if (content.hasMemoryAddress()) {
            pointer.set(content.memoryAddress() + content.readerIndex(), length);
            targetBytes.write(pointer);
            return;
        }

        byte[] scratch = new byte[length];
        content.getBytes(content.readerIndex(), scratch);
        targetBytes.write(scratch);
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
