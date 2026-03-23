package open.vincentf13.service.spot.ws.ws;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.MetricsCollector;
import open.vincentf13.service.spot.model.command.AbstractSbeModel;
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
    
    // 核心優化：ThreadLocal Appender 與 Pointer，消除爭用與一次內存拷貝
    private final ThreadLocal<ExcerptAppender> appenderThreadLocal = ThreadLocal.withInitial(wal::acquireAppender);
    private final ThreadLocal<PointerBytesStore> pointerThreadLocal = ThreadLocal.withInitial(PointerBytesStore::new);
    private final ThreadLocal<Long> localNettyRecvCount = ThreadLocal.withInitial(() -> 0L);
    private static final int METRICS_BATCH_SIZE = 5000;

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
        updateNettyMetrics();

        io.netty.buffer.ByteBuf content = frame.content();
        int length = content.readableBytes();
        if (length < 20) { 
            return; // 忽略無效封包
        }

        // 提取 MsgType (位於 SBE 固定偏移量 0)
        int msgType = content.getIntLE(content.readerIndex());
        
        // 處理 AUTH 以建立 Session 映射
        if (msgType == open.vincentf13.service.spot.infra.Constants.MsgType.AUTH) {
            // Layout: [Header 20][Timestamp 8][UserId 8]
            long userId = content.getLongLE(content.readerIndex() + 20 + 8);
            sessionManager.addSession(userId, ctx.channel());
        }

        // --- 核心優化：物理記憶體零拷貝直寫 WAL ---
        ExcerptAppender appender = appenderThreadLocal.get();
        PointerBytesStore pointer = pointerThreadLocal.get();
        
        try (var dc = appender.writingDocument()) {
            if (content.hasMemoryAddress()) {
                pointer.set(content.memoryAddress() + content.readerIndex(), length);
                dc.wire().bytes().write(pointer);
            } else {
                byte[] data = new byte[length];
                content.getBytes(content.readerIndex(), data);
                dc.wire().bytes().write(data);
            }
        } catch (Exception e) {
            log.error("[GATEWAY-WS] WAL 直寫失敗: {}", e.getMessage());
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 僅接受二進制框架，拒絕文字框架以節省性能
        if (msg instanceof TextWebSocketFrame textFrame) {
            log.warn("[GATEWAY-WS] 已停用 JSON 支持，忽略訊息: {}", textFrame.text());
            textFrame.release();
            return;
        }
        super.channelRead(ctx, msg);
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

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long uid = sessionManager.getUserIdByChannel(ctx.channel());
        if (uid != null) sessionManager.removeSession(uid, ctx.channel());
    }
}
