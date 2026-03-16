package open.vincentf13.service.spot.ws.ws;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.command.*;
import open.vincentf13.service.spot.sbe.Side;
import open.vincentf13.service.spot.ws.util.JsonUtil;
import org.agrona.MutableDirectBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 網關 WebSocket 指令處理器 (TPS 性能極限版)
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WsCommandInboundHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private final ChronicleQueue gatewaySenderWal = Storage.self().gatewaySenderWal();
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
    private long localWalWriteCount = 0;
    private static final int METRICS_BATCH_SIZE = 10000;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {     
        // 增加 Netty 接收指標 (批量)
        localNettyRecvCount++;
        if (localNettyRecvCount >= METRICS_BATCH_SIZE) {
            final long batch = localNettyRecvCount;
            Storage.self().metricsHistory().compute(Storage.KEY_NETTY_RECV_COUNT, (k, v) -> v == null ? batch : v + batch);
            localNettyRecvCount = 0;
        }

        log.debug("[GATEWAY-WS] 收到訊息: {}", frame.text()); // 降級為 debug 避免 I/O 阻塞
        final ThreadContext context = ThreadContext.get();
        final ThreadContext.RequestHolder holder = context.getRequestHolder();
        holder.reset();

        try {
            java.util.Map<String, Object> map = JsonUtil.toMap(frame.text());
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
            log.warn("JSON 解析失敗: {}, raw: {}", e.getMessage(), frame.text());
        }

        if (holder.getOp() == null) return;

        final MutableDirectBuffer scratch = context.getScratchBuffer().wrapForWrite();

        switch (holder.getOp()) {
            case "auth" -> {
                log.debug("[GATEWAY] 處理 AUTH: uid={}", holder.getUserId()); // 全面降級為 debug
                sessionManager.addSession(holder.getUserId(), ctx.channel());
                AuthCommand cmd = context.getAuthCommand();                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, System.currentTimeMillis(), holder.getUserId());
                writeRaw(cmd);
            }
            case "order_create" -> {
                log.debug("[GATEWAY] 處理 ORDER_CREATE: uid={}, sid={}, p={}, q={}, side={}, cid={}", 
                    holder.getUserId(), holder.getSymbolId(), holder.getPrice(), holder.getQty(), holder.getSide(), holder.getCid());
                Side side = "BUY".equalsIgnoreCase(holder.getSide()) ? Side.BUY : Side.SELL;
                OrderCreateCommand cmd = context.getOrderCreateCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, System.currentTimeMillis(), holder.getUserId(), holder.getSymbolId(), holder.getPrice(), holder.getQty(), side, holder.getCid());
                writeRaw(cmd);
            }
            case "order_cancel" -> {
                log.debug("[GATEWAY] 處理 ORDER_CANCEL: uid={}, oid={}", holder.getUserId(), holder.getOrderId());
                OrderCancelCommand cmd = context.getOrderCancelCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, System.currentTimeMillis(), holder.getUserId(), holder.getOrderId());
                writeRaw(cmd);
            }
            case "deposit" -> {
                log.debug("[GATEWAY] 處理 DEPOSIT: uid={}, aid={}, amt={}", holder.getUserId(), holder.getAssetId(), holder.getAmount());
                DepositCommand cmd = context.getDepositCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, System.currentTimeMillis(), holder.getUserId(), holder.getAssetId(), holder.getAmount());
                writeRaw(cmd);
            }
        }
    }

    private void writeRaw(AbstractSbeModel model) {
        try (DocumentContext dc = gatewaySenderWal.acquireAppender().writingDocument()) {
            net.openhft.chronicle.bytes.Bytes<?> bytes = dc.wire().bytes();
            // 調用模型的 writeMarshallable，它會自動處理 PointerBytesStore 並寫入內容
            model.writeMarshallable(bytes);
            
            localWalWriteCount++;
            if (localWalWriteCount >= METRICS_BATCH_SIZE) {
                final long batch = localWalWriteCount;
                Storage.self().metricsHistory().compute(Storage.KEY_GATEWAY_WAL_WRITE_COUNT, (k, v) -> v == null ? batch : v + batch);
                localWalWriteCount = 0;
            }
            log.debug("[GATEWAY-WAL] 訊息已持久化至 WAL, index={}", dc.index());
        }
    }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long uid = sessionManager.getUserIdByChannel(ctx.channel());
        if (uid != null) sessionManager.removeSession(uid, ctx.channel());
    }
}
