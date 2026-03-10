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

/**
 * WebSocket 指令處理器
 * 負責：JSON 解析、參數校驗、SBE 編碼、Aeron 轉發
 * 性能：採用 ThreadLocal 緩衝區實現 Zero-GC 指令轉換路徑
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final AttributeKey<String> ATTR_USER_ID = AttributeKey.valueOf("userId");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    // 用於主動推播的 Session 管理
    private final Map<String, Channel> userChannels = new ConcurrentHashMap<>();

    // --- Zero-GC 資源快取 ---
    private final ThreadLocal<Bytes<ByteBuffer>> bytesCache = ThreadLocal.withInitial(() -> Bytes.elasticByteBuffer(512));
    private final ThreadLocal<UnsafeBuffer> bufferCache = ThreadLocal.withInitial(() -> new UnsafeBuffer(0, 0));
    private final ThreadLocal<OrderCreateEncoder> orderEncoder = ThreadLocal.withInitial(OrderCreateEncoder::new);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        try {
            JsonNode root = MAPPER.readTree(frame.text());
            String op = root.path("op").asText("");
            
            switch (op) {
                case "order.create" -> handleOrderCreate(ctx.channel(), root);
                case "auth"         -> handleAuth(ctx.channel(), root);
                case "ping"         -> ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"op\":\"pong\"}"));
                default             -> log.warn("Unknown operation [{}] from channel {}", op, ctx.channel().id());
            }
        } catch (Exception e) {
            log.error("WebSocket message processing failed: {}", e.getMessage());
        }
    }

    /**
     * 處理建立訂單指令
     */
    private void handleOrderCreate(Channel channel, JsonNode root) {
        JsonNode params = root.get("params");
        if (params == null || params.isEmpty()) {
            log.warn("Order creation failed: missing params");
            return;
        }

        // 1. 準備 Zero-GC 緩衝區
        Bytes<ByteBuffer> bytes = bytesCache.get();
        bytes.clear();
        UnsafeBuffer buffer = bufferCache.get();
        buffer.wrap(bytes.addressForWrite(0), (int) bytes.realCapacity());
        
        OrderCreateEncoder encoder = orderEncoder.get();

        // 2. SBE 編碼轉換
        try {
            int len = SbeCodec.encode(buffer, 0, encoder
                .timestamp(System.currentTimeMillis())
                .userId(params.path("userId").asLong())
                .symbolId(params.path("symbolId").asLong())
                .price(params.path("price").asLong())
                .qty(params.path("qty").asLong())
                .side(Side.get((short) params.path("side").asInt(0)))
                .clientOrderId(root.path("cid").asText("")));

            bytes.writePosition(len);

            // 3. 寫入網關隊列 (Aeron 發送器的上游)
            Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
                wire.write("msgType").int32(encoder.sbeTemplateId());
                wire.write("payload").bytes(bytes);
            });
        } catch (Exception e) {
            log.error("SBE Encoding Error: {}", e.getMessage());
        }
    }

    /**
     * 處理用戶認證與 Session 綁定
     */
    private void handleAuth(Channel channel, JsonNode root) {
        long userId = root.path("args").path("userId").asLong(0);
        if (userId == 0) {
            log.warn("Auth failed: invalid userId");
            return;
        }

        String uidStr = String.valueOf(userId);
        userChannels.put(uidStr, channel);
        channel.attr(ATTR_USER_ID).set(uidStr);

        // 發送 Auth 指令到撮合核心執行資產初始化或檢查
        Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(103); // MSG_TYPE_AUTH
            wire.write("userId").int64(userId);
        });
        
        log.info("User {} authenticated on channel {}", userId, channel.id());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String uid = ctx.channel().attr(ATTR_USER_ID).get();
        if (uid != null) {
            userChannels.remove(uid);
            log.debug("User {} disconnected", uid);
        }
        super.channelInactive(ctx);
    }

    /**
     * 主動向特定用戶發送訊息 (由 PushWorker 調用)
     */
    public void sendMessage(String userId, String json) {
        Channel channel = userChannels.get(userId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new TextWebSocketFrame(json));
        }
    }
}
