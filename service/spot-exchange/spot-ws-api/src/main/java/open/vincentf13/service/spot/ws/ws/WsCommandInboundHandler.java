package open.vincentf13.service.spot.ws.ws;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.queue.ChronicleQueue;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import org.springframework.stereotype.Component;

import java.nio.ByteOrder;
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
        // 提前讀取 ByteBuf 欄位（Netty 保證 frame 在此方法內有效）
        final int msgType = content.getIntLE(content.readerIndex());
        final long sbeHeader = content.getLongLE(content.readerIndex() + 12);
        final int bodyLen = length - OLD_HEADER_SIZE;

        // synchronized 確保單一 DocumentContext 開啟，消除 Chronicle Queue hole。
        // acquireAppender() 在鎖內呼叫：每個 thread 取得自己的 appender（Chronicle thread-local），
        // 鎖防止多個 appender 同時持有開啟的 DocumentContext，從而杜絕 tailer 命中 hole。
        synchronized (wal) {
            try (var dc = wal.acquireAppender().writingDocument()) {
                if (dc.wire() == null) return;
                net.openhft.chronicle.bytes.Bytes<?> target = dc.wire().bytes();

                // 採用順序寫入 (Sequential Write)，不指定偏移量以防損壞 Wire Metadata
                target.writeInt(msgType);                                 // [0-3] MsgType
                target.writeInt(0);                                       // [4-7] Padding
                target.writeLong(0L);                                     // [8-15] Seq
                target.writeLong(arrivalTimeNs);                          // [16-23] GatewayTime
                target.writeLong(sbeHeader);                              // [24-31] SBE Header

                // 此時 Position 應自然位於 32，直接追加 Body
                if (bodyLen > 0) {
                    if (content.hasMemoryAddress()) {
                        final PointerBytesStore pointer = context.getReusablePointer();
                        pointer.set(content.memoryAddress() + content.readerIndex() + OLD_HEADER_SIZE, bodyLen);
                        target.write(pointer);
                    } else {
                        byte[] scratch = new byte[bodyLen];
                        content.getBytes(content.readerIndex() + OLD_HEADER_SIZE, scratch);
                        target.write(scratch);
                    }
                }
            } catch (Exception e) {
                log.error("[GATEWAY-WS] WAL 直寫失敗", e);
            }
        }

        StaticMetricsHolder.addCounter(MetricsKey.GATEWAY_WAL_WRITE_COUNT, 1);
        if (authenticatedUserId != null) sessionManager.addSession(authenticatedUserId, ctx.channel());
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
