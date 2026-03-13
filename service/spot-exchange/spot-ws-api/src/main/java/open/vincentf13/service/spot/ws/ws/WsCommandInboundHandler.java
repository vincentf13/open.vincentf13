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
import open.vincentf13.service.spot.infra.alloc.SbeCodec;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.command.AuthCommand;
import open.vincentf13.service.spot.model.command.DepositCommand;
import open.vincentf13.service.spot.model.command.OrderCancelCommand;
import open.vincentf13.service.spot.model.command.OrderCreateCommand;
import open.vincentf13.service.spot.sbe.Side;
import open.vincentf13.service.spot.ws.util.JsonUtil;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 WebSocket 指令入口處理器 (Ws Command Inbound Handler)
 職責：解析 JSON 指令、編碼 SBE 並原子化地寫入 GatewaySenderWal
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
        ByteBuf content = frame.content();
        if (!content.isReadable()) return;

        ThreadContext.RequestHolder holder = ThreadContext.get().getRequestHolder();
        holder.reset();

        try (JsonParser parser = JsonUtil.MAPPER.getFactory().createParser(Unpooled.copiedBuffer(content).array())) {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.getCurrentName();
                if (field == null) continue;
                parser.nextToken();
                switch (field) {
                    case Ws.OP -> holder.setOp(parser.getText());
                    case Ws.USER_ID -> holder.setUserId(parser.getLongValue());
                    case Ws.ORDER_ID -> holder.setOrderId(parser.getLongValue());
                    case Ws.SYMBOL_ID -> holder.setSymbolId(parser.getIntValue());
                    case Ws.ASSET_ID -> holder.setAssetId(parser.getIntValue());
                    case Ws.PRICE -> holder.setPrice(parser.getLongValue());
                    case Ws.QTY -> holder.setQty(parser.getLongValue());
                    case Ws.SIDE -> holder.setSide(parser.getText());
                    case Ws.CID -> holder.setCid(parser.getLongValue());
                    case Ws.AMOUNT -> holder.setAmount(parser.getLongValue());
                }
            }

            if (Ws.PING.equals(holder.getOp())) {
                ctx.writeAndFlush(new TextWebSocketFrame("{\"op\":\"pong\"}"));
            } else if (Ws.AUTH.equals(holder.getOp())) {
                handleAuth(ctx.channel(), holder);
            } else if (Ws.CREATE.equals(holder.getOp())) {
                handleOrderCreate(ctx.channel(), holder);
            } else if (Ws.CANCEL.equals(holder.getOp())) {
                handleOrderCancel(ctx.channel(), holder);
            } else if (Ws.DEPOSIT.equals(holder.getOp())) {
                handleDeposit(ctx.channel(), holder);
            }
        }
    }

    private void handleAuth(Channel channel, ThreadContext.RequestHolder holder) {
        sessionManager.addSession(holder.getUserId(), channel);
        
        AuthCommand cmd = ThreadContext.get().getAuthCommand();
        int sbeLen = SbeCodec.encodeToScratchAuth(System.currentTimeMillis(), holder.getUserId());
        cmd.fillFromScratch(sbeLen);
        
        try (DocumentContext dc = gatewaySenderWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.AUTH);
            dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(cmd);
        }
    }
    
    private void handleOrderCancel(Channel channel, ThreadContext.RequestHolder holder) {
        OrderCancelCommand cmd = ThreadContext.get().getOrderCancelCommand();
        int sbeLen = SbeCodec.encodeToScratchOrderCancel(System.currentTimeMillis(), holder.getUserId(), holder.getOrderId());
        cmd.fillFromScratch(sbeLen);

        try (DocumentContext dc = gatewaySenderWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.ORDER_CANCEL);
            dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(cmd);
        }
    }

    private void handleOrderCreate(Channel channel, ThreadContext.RequestHolder holder) {
        OrderCreateCommand cmd = ThreadContext.get().getOrderCreateCommand();
        Side side = "BUY".equalsIgnoreCase(holder.getSide()) ? Side.BUY : Side.SELL;
        
        int sbeLen = SbeCodec.encodeToScratchOrderCreate(System.currentTimeMillis(), holder.getUserId(), 
                holder.getSymbolId(), holder.getPrice(), holder.getQty(), side, holder.getCid());
        cmd.fillFromScratch(sbeLen);

        try (DocumentContext dc = gatewaySenderWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.ORDER_CREATE);
            dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(cmd);
        }
    }

    private void handleDeposit(Channel channel, ThreadContext.RequestHolder holder) {
        DepositCommand cmd = ThreadContext.get().getDepositCommand();
        int sbeLen = SbeCodec.encodeToScratchDeposit(System.currentTimeMillis(), holder.getUserId(), 
                holder.getAssetId(), holder.getAmount());
        cmd.fillFromScratch(sbeLen);

        try (DocumentContext dc = gatewaySenderWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.DEPOSIT);
            dc.wire().write(ChronicleWireKey.payload).bytesMarshallable(cmd);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long uid = sessionManager.getUserIdByChannel(ctx.channel());
        if (uid != null) {
            sessionManager.removeSession(uid, ctx.channel());
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,
                                Throwable cause) {
        ctx.close();
    }
}
