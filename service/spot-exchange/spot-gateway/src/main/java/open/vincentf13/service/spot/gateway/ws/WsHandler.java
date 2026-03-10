package open.vincentf13.service.spot.gateway.ws;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.infra.util.JsonUtil;
import open.vincentf13.service.spot.model.OrderRequest;
import open.vincentf13.service.spot.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static open.vincentf13.service.spot.infra.Constants.*;

@Slf4j
@Component
@ChannelHandler.Sharable
public class WsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final AttributeKey<String> ATTR_USER_ID = AttributeKey.valueOf(FIELD_USER_ID);
    private final Map<String, Channel> userChannels = new ConcurrentHashMap<>();

    // --- Zero-GC 資源池 ---
    private final ThreadLocal<Bytes<ByteBuffer>> bytesCache = ThreadLocal.withInitial(() -> Bytes.elasticByteBuffer(512));
    private final ThreadLocal<UnsafeBuffer> bufferCache = ThreadLocal.withInitial(() -> new UnsafeBuffer(0, 0));
    private final ThreadLocal<OrderCreateEncoder> orderEncoder = ThreadLocal.withInitial(OrderCreateEncoder::new);
    
    // --- 關鍵優化：重用 Request 物件，徹底消除此路徑的物件分配 ---
    private final ThreadLocal<OrderRequest> reusableOrderRequest = ThreadLocal.withInitial(OrderRequest::new);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String text = frame.text();
        if (text.contains(OP_PING)) { ctx.channel().writeAndFlush(new TextWebSocketFrame(RESP_PONG)); return; }

        // 初步解析獲取操作類型 (這裡仍會分配 JsonNode，但在極致場景下可改用字串掃描)
        var root = JsonUtil.readTree(text);
        if (root == null) return;

        String op = root.path(FIELD_OP).asText("");
        if (OP_ORDER_CREATE.equals(op)) {
            // 使用重用物件解析，避免 new OrderRequest()
            OrderRequest req = reusableOrderRequest.get();
            req.reset();
            JsonUtil.updateObject(text, req);
            handleOrderCreate(ctx.channel(), req);
        } else if (OP_AUTH.equals(op)) {
            handleAuth(ctx.channel(), root);
        }
    }

    private void handleOrderCreate(Channel channel, OrderRequest req) {
        OrderRequest.Params p = req.getParams();
        // 這裡 p 也是預初始化的，不需要檢查 null
        
        Bytes<ByteBuffer> bytes = bytesCache.get(); bytes.clear();
        UnsafeBuffer buffer = bufferCache.get(); buffer.wrap(bytes.addressForWrite(0), (int) bytes.realCapacity());
        OrderCreateEncoder encoder = orderEncoder.get();

        int len = SbeCodec.encode(buffer, 0, encoder.timestamp(System.currentTimeMillis())
            .userId(p.getUserId()).symbolId(p.getSymbolId())
            .price(p.getPrice()).qty(p.getQty())
            .side(Side.get((short) p.getSide())).clientOrderId(req.getCid()));

        bytes.writePosition(len);
        Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(encoder.sbeTemplateId()); wire.write("payload").bytes(bytes);
        });
    }

    private void handleAuth(Channel channel, com.fasterxml.jackson.databind.JsonNode root) {
        long userId = root.path(FIELD_ARGS).path(FIELD_USER_ID).asLong(0);
        if (userId == 0) return;

        String uidStr = String.valueOf(userId);
        userChannels.put(uidStr, channel);
        channel.attr(ATTR_USER_ID).set(uidStr);

        Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(MSG_TYPE_AUTH); wire.write(FIELD_USER_ID).int64(userId);
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String uid = ctx.channel().attr(ATTR_USER_ID).get();
        if (uid != null) userChannels.remove(uid);
        super.channelInactive(ctx);
    }

    public void sendMessage(String userId, String json) {
        Channel channel = userChannels.get(userId);
        if (channel != null && channel.isActive()) channel.writeAndFlush(new TextWebSocketFrame(json));
    }
}
