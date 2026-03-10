package open.vincentf13.service.spot_exchange.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.service.spot_exchange.infra.SbeCodec;
import open.vincentf13.service.spot_exchange.infra.StateStore;
import open.vincentf13.service.spot_exchange.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot_exchange.sbe.Side;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import net.openhft.chronicle.bytes.Bytes;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExchangeWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ExchangeWebSocketHandler.class);
    private final StateStore stateStore;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    // --- 使用 ThreadLocal 管理 Chronicle Bytes 與 SBE Encoder ---
    private static final ThreadLocal<Bytes<ByteBuffer>> BYTES_CACHE = 
        ThreadLocal.withInitial(() -> Bytes.elasticByteBuffer(512));
    private static final ThreadLocal<UnsafeBuffer> BUFFER_CACHE = 
        ThreadLocal.withInitial(() -> new UnsafeBuffer(0, 0));
    private static final ThreadLocal<OrderCreateEncoder> ENCODER_CACHE = 
        ThreadLocal.withInitial(OrderCreateEncoder::new);

    public ExchangeWebSocketHandler(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String op = node.has("op") ? node.get("op").asText() : "";

            if ("order.create".equals(op)) {
                handleOrderCreate(node);
            } else if ("auth".equals(op)) {
                handleAuth(session, node);
            } else {
                log.warn("Unknown operation: {}", op);
            }
        } catch (Exception e) {
            log.error("Handle WS message error: {}", e.getMessage(), e);
        }
    }

    private void handleOrderCreate(JsonNode node) {
        JsonNode params = node.get("params");
        if (params == null) return;

        Bytes<ByteBuffer> bytes = BYTES_CACHE.get();
        bytes.clear();
        
        UnsafeBuffer buffer = BUFFER_CACHE.get();
        buffer.wrap(bytes.addressForWrite(0), (int)bytes.realCapacity());
        
        OrderCreateEncoder encoder = ENCODER_CACHE.get();

        int len = SbeCodec.encode(buffer, 0, encoder
            .timestamp(System.currentTimeMillis())
            .userId(params.get("userId").asLong())
            .symbolId(params.get("symbolId").asLong())
            .price(params.get("price").asLong())
            .qty(params.get("qty").asLong())
            .side(Side.get((short) params.get("side").asInt()))
            .clientOrderId(node.get("cid").asText()));

        bytes.writePosition(len);

        stateStore.getGwQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(encoder.sbeTemplateId());
            wire.write("payload").bytes(bytes);
        });
    }

    private void handleAuth(WebSocketSession session, JsonNode node) {
        JsonNode args = node.get("args");
        if (args == null || !args.has("userId")) return;

        String userId = args.get("userId").asText();
        userSessions.put(userId, session);
        session.getAttributes().put("userId", userId);
        
        stateStore.getGwQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(103);
            wire.write("userId").int64(Long.parseLong(userId));
        });
        log.info("User auth success: {}", userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.remove(userId);
            log.info("WebSocket closed, clean user resource: {}", userId);
        }
    }

    public void sendMessage(String userId, String json) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try { 
                session.sendMessage(new TextMessage(json)); 
            } catch (Exception e) { 
                log.error("WS Push Error for user {}: {}", userId, e.getMessage()); 
            }
        }
    }
}
