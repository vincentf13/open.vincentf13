package open.vincentf13.service.spot.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import open.vincentf13.service.spot.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ChannelHandler.Sharable
public class WsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final AttributeKey<String> USER_ID_KEY = AttributeKey.valueOf("userId");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final Map<String, Channel> userChannels = new ConcurrentHashMap<>();

    private final ThreadLocal<Bytes<ByteBuffer>> bytesCache = ThreadLocal.withInitial(() -> Bytes.elasticByteBuffer(512));
    private final ThreadLocal<UnsafeBuffer> bufferCache = ThreadLocal.withInitial(() -> new UnsafeBuffer(0, 0));
    private final ThreadLocal<OrderCreateEncoder> encoderCache = ThreadLocal.withInitial(OrderCreateEncoder::new);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        try {
            JsonNode node = MAPPER.readTree(frame.text());
            String op = node.has("op") ? node.get("op").asText() : "";
            if ("order.create".equals(op)) handleOrderCreate(node);
            else if ("auth".equals(op)) handleAuth(ctx.channel(), node);
        } catch (Exception e) { log.error("Netty WS error: {}", e.getMessage()); }
    }

    private void handleOrderCreate(JsonNode node) {
        JsonNode params = node.get("params");
        if (params == null) return;
        Bytes<ByteBuffer> bytes = bytesCache.get(); bytes.clear();
        UnsafeBuffer buffer = bufferCache.get(); buffer.wrap(bytes.addressForWrite(0), (int)bytes.realCapacity());
        OrderCreateEncoder encoder = encoderCache.get();

        int len = SbeCodec.encode(buffer, 0, encoder.timestamp(System.currentTimeMillis())
            .userId(params.get("userId").asLong()).symbolId(params.get("symbolId").asLong())
            .price(params.get("price").asLong()).qty(params.get("qty").asLong())
            .side(Side.get((short) params.get("side").asInt())).clientOrderId(node.get("cid").asText()));

        bytes.writePosition(len);
        Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(encoder.sbeTemplateId()); wire.write("payload").bytes(bytes);
        });
    }

    private void handleAuth(Channel channel, JsonNode node) {
        String uid = node.get("args").get("userId").asText();
        userChannels.put(uid, channel);
        channel.attr(USER_ID_KEY).set(uid);
        Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(103); wire.write("userId").int64(Long.parseLong(uid));
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String uid = ctx.channel().attr(USER_ID_KEY).get();
        if (uid != null) userChannels.remove(uid);
        super.channelInactive(ctx);
    }

    public void sendMessage(String uid, String json) {
        Channel c = userChannels.get(uid);
        if (c != null && c.isActive()) c.writeAndFlush(new TextWebSocketFrame(json));
    }
}
