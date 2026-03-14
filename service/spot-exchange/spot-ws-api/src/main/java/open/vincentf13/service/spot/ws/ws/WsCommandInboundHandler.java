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
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
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
            log.warn("JSON 解析失敗: {}", e.getMessage());
        }

        if (holder.getOp() == null) return;

        final MutableDirectBuffer scratch = context.getScratchBuffer().wrapForWrite();

        switch (holder.getOp()) {
            case "auth" -> {
                sessionManager.addSession(holder.getUserId(), ctx.channel());
                AuthCommand cmd = context.getAuthCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, System.currentTimeMillis(), holder.getUserId());
                writeRaw(cmd);
            }
            case "order_create" -> {
                Side side = "BUY".equalsIgnoreCase(holder.getSide()) ? Side.BUY : Side.SELL;
                OrderCreateCommand cmd = context.getOrderCreateCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, System.currentTimeMillis(), holder.getUserId(), holder.getSymbolId(), holder.getPrice(), holder.getQty(), side, holder.getCid());
                writeRaw(cmd);
            }
            case "order_cancel" -> {
                OrderCancelCommand cmd = context.getOrderCancelCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, System.currentTimeMillis(), holder.getUserId(), holder.getOrderId());
                writeRaw(cmd);
            }
            case "deposit" -> {
                DepositCommand cmd = context.getDepositCommand();
                cmd.wrapWriteBuffer(scratch, 0);
                cmd.set(MSG_SEQ_NONE, System.currentTimeMillis(), holder.getUserId(), holder.getAssetId(), holder.getAmount());
                writeRaw(cmd);
            }
        }
    }

    private void writeRaw(AbstractSbeModel model) {
        try (DocumentContext dc = gatewaySenderWal.acquireAppender().writingDocument()) {
            Bytes<?> bytes = dc.wire().bytes();
            bytes.write(model.getPointerBytesStore());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long uid = sessionManager.getUserIdByChannel(ctx.channel());
        if (uid != null) sessionManager.removeSession(uid, ctx.channel());
    }
}
