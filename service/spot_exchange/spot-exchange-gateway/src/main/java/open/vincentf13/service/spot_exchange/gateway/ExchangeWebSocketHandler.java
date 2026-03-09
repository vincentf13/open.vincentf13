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

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExchangeWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ExchangeWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StateStore stateStore;
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    private static final ThreadLocal<UnsafeBuffer> BUFFER_CACHE = 
        ThreadLocal.withInitial(() -> new UnsafeBuffer(ByteBuffer.allocateDirect(512)));
    private static final ThreadLocal<OrderCreateEncoder> ENCODER_CACHE = 
        ThreadLocal.withInitial(OrderCreateEncoder::new);

    public ExchangeWebSocketHandler(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String op = node.get("op").asText();

        if ("order.create".equals(op)) {
            UnsafeBuffer buffer = BUFFER_CACHE.get();
            OrderCreateEncoder encoder = ENCODER_CACHE.get();

            int len = SbeCodec.encode(buffer, 0, encoder
                .timestamp(System.currentTimeMillis())
                .userId(node.get("params").get("userId").asLong())
                .symbolId(node.get("params").get("symbolId").asLong())
                .price(node.get("params").get("price").asLong())
                .qty(node.get("params").get("qty").asLong())
                .side(Side.get(node.get("params").get("side").asInt()))
                .clientOrderId(node.get("cid").asText()));

            // --- 修正：使用正確的方式寫入 DirectBuffer 內容 ---
            stateStore.getGwQueue().acquireAppender().writeDocument(wire -> {
                wire.write("msgType").int32(encoder.sbeTemplateId());
                // 透過 bytes(buffer, offset, length) 直接處理 DirectBuffer
                wire.write("payload").bytes(buffer, 0, len);
            });
        } else if ("auth".equals(op)) {
            String userId = node.get("args").get("userId").asText();
            userSessions.put(userId, session);
            stateStore.getGwQueue().acquireAppender().writeDocument(wire -> {
                wire.write("msgType").int32(103);
                wire.write("userId").int64(Long.parseLong(userId));
            });
        }
    }

    public void sendMessage(String userId, String json) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try { session.sendMessage(new TextMessage(json)); } 
            catch (Exception e) { log.error("WS Push Error: {}", userId, e); }
        }
    }
}
