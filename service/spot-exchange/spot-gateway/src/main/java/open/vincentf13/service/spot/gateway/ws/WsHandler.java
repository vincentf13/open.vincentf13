package open.vincentf13.service.spot.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
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
import open.vincentf13.service.spot.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 指令處理器
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final AttributeKey<String> ATTR_USER_ID = AttributeKey.valueOf("userId");
    private final Map<String, Channel> userChannels = new ConcurrentHashMap<>();

    private final ThreadLocal<Bytes<ByteBuffer>> bytesCache = ThreadLocal.withInitial(() -> Bytes.elasticByteBuffer(512));
    private final ThreadLocal<UnsafeBuffer> bufferCache = ThreadLocal.withInitial(() -> new UnsafeBuffer(0, 0));
    private final ThreadLocal<OrderCreateEncoder> orderEncoder = ThreadLocal.withInitial(OrderCreateEncoder::new);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        JsonNode root = JsonUtil.readTree(frame.text());
        if (root == null) return;

        String op = root.path("op").asText("");
        switch (op) {
            case "order.create" -> handleOrderCreate(ctx.channel(), root);
            case "auth"         -> handleAuth(ctx.channel(), root);
            case "ping"         -> ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"op\":\"pong\"}"));
            default             -> log.warn("Unknown operation [{}]", op);
        }
    }

    private void handleOrderCreate(Channel channel, JsonNode root) {
        JsonNode params = root.get("params");
        if (params == null) return;

        Bytes<ByteBuffer> bytes = bytesCache.get(); bytes.clear();
        UnsafeBuffer buffer = bufferCache.get(); buffer.wrap(bytes.addressForWrite(0), (int) bytes.realCapacity());
        OrderCreateEncoder encoder = orderEncoder.get();

        int len = SbeCodec.encode(buffer, 0, encoder.timestamp(System.currentTimeMillis())
            .userId(params.path("userId").asLong()).symbolId(params.path("symbolId").asLong())
            .price(params.path("price").asLong()).qty(params.path("qty").asLong())
            .side(Side.get((short) params.path("side").asInt(0))).clientOrderId(root.path("cid").asText("")));

        bytes.writePosition(len);
        Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(encoder.sbeTemplateId()); wire.write("payload").bytes(bytes);
        });
    }

    private void handleAuth(Channel channel, JsonNode root) {
        long userId = root.path("args").path("userId").asLong(0);
        if (userId == 0) return;

        String uidStr = String.valueOf(userId);
        userChannels.put(uidStr, channel);
        channel.attr(ATTR_USER_ID).set(uidStr);

        Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(103); wire.write("userId").int64(userId);
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
