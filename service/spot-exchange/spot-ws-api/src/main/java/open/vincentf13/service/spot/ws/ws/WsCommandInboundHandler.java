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
    private static final int OLD_HEADER_SIZE = 20;
    private static final int MIN_COMMAND_LENGTH = OLD_HEADER_SIZE;
    private static final int MIN_AUTH_LENGTH = 36;

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

        // 提取舊版 Header (20 bytes) 中的 UserId
        Long authenticatedUserId = extractAuthUserId(ctx, content, length);

        final ThreadContext context = ThreadContext.get();
        final ExcerptAppender appender = appenderThreadLocal.get();
        
        try (var dc = appender.writingDocument()) {
            net.openhft.chronicle.bytes.Bytes<?> target = dc.wire().bytes();
            int msgType = content.getIntLE(content.readerIndex());
            
            // 寫入新版 32-byte 標頭，並自動補齊 Padding
            target.writeLong(msgType & 0xFFFFFFFFL); // [0-7] MsgType + Padding
            target.writeLong(0);                      // [8-15] Seq (由發送端填寫)
            target.writeLong(arrivalTimeNs);          // [16-23] GatewayTime
            target.writeLong(content.getLongLE(content.readerIndex() + 12)); // [24-31] SBE Header (從舊偏移量 12 提取)
            
            // 寫入 Body 部分 (從舊偏移量 20 開始，映射至新偏移量 32)
            int bodyLen = length - OLD_HEADER_SIZE;
            if (bodyLen > 0) {
                if (content.hasMemoryAddress()) {
                    target.write(content.memoryAddress() + content.readerIndex() + OLD_HEADER_SIZE, bodyLen);
                } else {
                    byte[] scratch = new byte[bodyLen];
                    content.getBytes(content.readerIndex() + OLD_HEADER_SIZE, scratch);
                    target.write(scratch);
                }
            }

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

        // 在舊版 20 標頭結構下，UserId 位於 28 (20 + 8)
        long userId = content.getLongLE(readerIndex + 28);
        return userId > 0 ? userId : null;
    }

    // 移除未使用的 writeFrameToWal 函式

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
