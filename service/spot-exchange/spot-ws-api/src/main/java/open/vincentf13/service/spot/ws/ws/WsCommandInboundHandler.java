package open.vincentf13.service.spot.ws.ws;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.alloc.NativeUnsafeBuffer;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.sbe.Side;
import open.vincentf13.service.spot.ws.util.JsonUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * WebSocket 指令入站處理器 (WsCommandInboundHandler)
 * 職責：解析用戶指令並將其寫入 WAL，同時調用 WsSessionManager 註冊連線
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WsCommandInboundHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private final ChronicleQueue clientToGwWal = Storage.self().clientToGwWal();
    private final WsSessionManager sessionManager;

    private static final ByteBuf PONG_BUF = Unpooled.unreleasableBuffer(
            Unpooled.copiedBuffer(Ws.PONG, StandardCharsets.UTF_8));

    public WsCommandInboundHandler(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        ThreadContext.RequestHolder holder = ThreadContext.get().getRequestHolder();
        holder.reset();

        try (JsonParser parser = JsonUtil.createParser(frame.content())) {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.getCurrentName();
                if (field == null)
                    continue;
                parser.nextToken();
                switch (field) {
                    case Ws.OP -> holder.setOp(parser.getText());
                    case Ws.USER_ID -> holder.setUserId(parser.getLongValue());
                    case Ws.ORDER_ID -> holder.setOrderId(parser.getLongValue());
                    case Ws.SYMBOL_ID -> holder.setSymbolId(parser.getIntValue());
                    case Ws.PRICE -> holder.setPrice(parser.getLongValue());
                    case Ws.QTY -> holder.setQty(parser.getLongValue());
                    case Ws.SIDE -> holder.setSide(parser.getText());
                    case Ws.CID -> holder.setCid(parser.getLongValue());
                    case Ws.ASSET_ID -> holder.setAssetId(parser.getIntValue());
                    case Ws.AMOUNT -> holder.setAmount(parser.getLongValue());
                }
            }

            if (Ws.PING.equals(holder.getOp())) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame(PONG_BUF.duplicate()));
            } else if (Ws.AUTH.equals(holder.getOp())) {
                handleAuth(ctx.channel(), holder);
            } else if (Ws.CREATE.equals(holder.getOp())) {
                handleOrderCreate(ctx.channel(), holder);
            } else if (Ws.CANCEL.equals(holder.getOp())) {
                handleOrderCancel(ctx.channel(), holder);
            } else if (Ws.DEPOSIT.equals(holder.getOp())) {
                handleDeposit(ctx.channel(), holder);
            }
        } catch (Exception e) {
            log.error("指令解析失敗: {}", e.getMessage());
        }
    }

    private void handleAuth(Channel channel, ThreadContext.RequestHolder holder) {
        sessionManager.addSession(holder.getUserId(), channel);

        open.vincentf13.service.spot.model.command.AuthCommand cmd = ThreadContext.get().getAuthCommand();
        cmd.setUserId(holder.getUserId());
        cmd.setSeq(0); // Sequence is assigned by Aeron Receiver later

        try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.AUTH);
            dc.wire().write(ChronicleWireKey.payload).marshallable(cmd);
        }
    }

    private void handleOrderCancel(Channel channel, ThreadContext.RequestHolder holder) {
        Long uid = channel.attr(WsSessionManager.USER_ID_KEY).get();
        if (uid == null)
            return;

        open.vincentf13.service.spot.model.command.OrderCancelCommand cmd = ThreadContext.get().getOrderCancelCommand();
        cmd.setUserId(uid);
        cmd.setOrderId(holder.getOrderId());
        cmd.setSeq(0);

        try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.ORDER_CANCEL);
            dc.wire().write(ChronicleWireKey.payload).marshallable(cmd);
        }
    }

    private void handleDeposit(Channel channel, ThreadContext.RequestHolder holder) {
        Long uid = channel.attr(WsSessionManager.USER_ID_KEY).get();
        if (uid == null)
            return;

        open.vincentf13.service.spot.model.command.DepositCommand cmd = ThreadContext.get().getDepositCommand();
        cmd.setUserId(uid);
        cmd.setAssetId(holder.getAssetId());
        cmd.setAmount(holder.getAmount());
        cmd.setSeq(0);

        try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.DEPOSIT);
            dc.wire().write(ChronicleWireKey.payload).marshallable(cmd);
        }
    }

    private void handleOrderCreate(Channel channel, ThreadContext.RequestHolder holder) {
        Long uid = channel.attr(WsSessionManager.USER_ID_KEY).get();
        if (uid == null)
            return;

        NativeUnsafeBuffer scratchBuffer = ThreadContext.get().getScratchBuffer();
        scratchBuffer.clear();
        UnsafeBuffer sbeBuffer = scratchBuffer.wrapForWrite();

        int sbeLen = SbeCodec.encode(sbeBuffer, 0, ThreadContext.get().getOrderCreateEncoder()
                .userId(uid).symbolId(holder.getSymbolId()).price(holder.getPrice())
                .qty(holder.getQty()).side(Side.valueOf(holder.getSide())).clientOrderId(holder.getCid())
                .timestamp(System.currentTimeMillis()));

        open.vincentf13.service.spot.model.command.OrderCreateCommand cmd = ThreadContext.get().getOrderCreateCommand();
        cmd.fillFrom(sbeBuffer, 0, sbeLen, 0);

        try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.ORDER_CREATE);
            dc.wire().write(ChronicleWireKey.payload).marshallable(cmd);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long uid = ctx.channel().attr(WsSessionManager.USER_ID_KEY).get();
        if (uid != null) {
            sessionManager.removeSession(uid, ctx.channel());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
