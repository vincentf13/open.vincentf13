package open.vincentf13.service.spot.gateway.ws;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.infra.util.JsonUtil;
import open.vincentf13.service.spot.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot.sbe.Side;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 WebSocket 接入處理器 (WsHandler)
 職責：管理客戶端連線、執行高性能 JSON 解析並原子寫入指令流 WAL
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private final ChronicleQueue clientToGwWal = Storage.self().clientToGwWal();
    private final Map<String, Channel> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    
    private final OrderCreateEncoder createEncoder = new OrderCreateEncoder();
    private final Bytes<ByteBuffer> sbeBytes = Bytes.elasticByteBuffer(512);
    private final UnsafeBuffer sbeBuffer = new UnsafeBuffer(0, 0);

    @Data
    private static class RequestHolder {
        String op; long userId; int symbolId; long price; long qty; String side; long cid;
        void reset() { op = null; userId = 0; symbolId = 0; price = 0; qty = 0; side = null; cid = 0; }
    }

    private final ThreadLocal<RequestHolder> holderPool = ThreadLocal.withInitial(RequestHolder::new);

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        sessions.put(ctx.channel().id().asLongText(), ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        RequestHolder holder = holderPool.get();
        holder.reset();

        try (JsonParser parser = JsonUtil.createParser(frame.content())) {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.getCurrentName();
                if (field == null) continue;
                parser.nextToken();
                switch (field) {
                    case Ws.OP -> holder.op = parser.getText();
                    case Ws.USER_ID -> holder.userId = parser.getLongValue();
                    case Ws.SYMBOL_ID -> holder.symbolId = parser.getIntValue();
                    case Ws.PRICE -> holder.price = parser.getLongValue();
                    case Ws.QTY -> holder.qty = parser.getLongValue();
                    case Ws.SIDE -> holder.side = parser.getText();
                    case Ws.CID -> holder.cid = parser.getLongValue();
                }
            }

            if (Ws.PING.equals(holder.op)) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame(Ws.PONG));
            } else if (Ws.AUTH.equals(holder.op)) {
                handleAuth(ctx.channel(), holder);
            } else if (Ws.CREATE.equals(holder.op)) {
                handleOrderCreate(ctx.channel(), holder);
            }
        } catch (Exception e) {
            log.error("指令解析異常: {}", e.getMessage());
        }
    }

    private void handleAuth(Channel channel, RequestHolder holder) {
        sessionToUser.put(channel.id().asLongText(), String.valueOf(holder.userId));
        try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.AUTH);
            dc.wire().write(ChronicleWireKey.userId).int64(holder.userId);
        }
    }

    private void handleOrderCreate(Channel channel, RequestHolder holder) {
        String uid = sessionToUser.get(channel.id().asLongText());
        if (uid == null) return;

        synchronized (sbeBytes) {
            sbeBytes.clear();
            sbeBuffer.wrap(sbeBytes.addressForWrite(0), (int) sbeBytes.realCapacity());
            int sbeLen = SbeCodec.encode(sbeBuffer, 0, createEncoder
                .userId(Long.parseLong(uid)).symbolId(holder.symbolId).price(holder.price)
                .qty(holder.qty).side(Side.valueOf(holder.side)).clientOrderId(holder.cid)
                .timestamp(System.currentTimeMillis()));
            sbeBytes.writePosition(sbeLen);

            try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
                dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.ORDER_CREATE);
                dc.wire().write(ChronicleWireKey.payload).bytes(sbeBytes);
            }
        }
    }

    public void sendMessage(String userId, String json) {
        sessions.values().stream()
            .filter(c -> String.valueOf(userId).equals(sessionToUser.get(c.id().asLongText())))
            .forEach(c -> c.writeAndFlush(new TextWebSocketFrame(json)));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        sessions.remove(ctx.channel().id().asLongText());
        sessionToUser.remove(ctx.channel().id().asLongText());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
