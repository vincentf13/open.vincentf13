package open.vincentf13.service.spot.ws.ws;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.command.*;
import open.vincentf13.service.spot.sbe.Side;
import open.vincentf13.service.spot.ws.util.JsonUtil;
import org.agrona.concurrent.UnsafeBuffer;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 網關 WebSocket 指令處理器 (Unified Model Edition)
 */
@Slf4j
public class WsCommandInboundHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private final ChronicleQueue gatewaySenderWal = Storage.self().gatewaySenderWal();
    private final WsSessionManager sessionManager;
    private final UnsafeBuffer scratch = new UnsafeBuffer(new byte[256]); // 用於暫存編碼結果

    public WsCommandInboundHandler(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        final ThreadContext context = ThreadContext.get();
        final ThreadContext.RequestHolder holder = context.getRequestHolder();
        holder.reset();

        try (JsonParser parser = JsonUtil.toMap(frame.text()).entrySet().iterator().next().getValue().toString().isEmpty() ? null : null) {
            // 簡化：這裡我們直接用 JsonUtil.toMap，雖然稍微慢一點但 API 穩定
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

        switch (holder.getOp()) {
            case "auth" -> {
                sessionManager.addSession(holder.getUserId(), ctx.channel());
                AuthCommand cmd = context.getAuthCommand();
                cmd.write(scratch, 0, MSG_SEQ_NONE).userId(holder.getUserId()).timestamp(System.currentTimeMillis());
                writeCommand(MsgType.AUTH, cmd);
            }
            case "order_create" -> {
                Side side = "BUY".equalsIgnoreCase(holder.getSide()) ? Side.BUY : Side.SELL;
                OrderCreateCommand cmd = context.getOrderCreateCommand();
                cmd.write(scratch, 0, MSG_SEQ_NONE)
                    .userId(holder.getUserId()).symbolId(holder.getSymbolId())
                    .price(holder.getPrice()).qty(holder.getQty()).side(side)
                    .clientOrderId(holder.getCid()).timestamp(System.currentTimeMillis());
                writeCommand(MsgType.ORDER_CREATE, cmd);
            }
            case "order_cancel" -> {
                OrderCancelCommand cmd = context.getOrderCancelCommand();
                cmd.write(scratch, 0, MSG_SEQ_NONE).userId(holder.getUserId()).orderId(holder.getOrderId()).timestamp(System.currentTimeMillis());
                writeCommand(MsgType.ORDER_CANCEL, cmd);
            }
            case "deposit" -> {
                DepositCommand cmd = context.getDepositCommand();
                cmd.write(scratch, 0, MSG_SEQ_NONE).userId(holder.getUserId()).assetId(holder.getAssetId()).amount(holder.getAmount()).timestamp(System.currentTimeMillis());
                writeCommand(MsgType.DEPOSIT, cmd);
            }
        }
    }

    private void writeCommand(int msgType, AbstractSbeModel model) {
        try (DocumentContext dc = gatewaySenderWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(msgType);
            dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(model);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long uid = sessionManager.getUserIdByChannel(ctx.channel());
        if (uid != null) sessionManager.removeSession(uid, ctx.channel());
    }
}
