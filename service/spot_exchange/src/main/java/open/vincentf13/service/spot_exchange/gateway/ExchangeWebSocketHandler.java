package open.vincentf13.service.spot_exchange.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.service.spot_exchange.core.InboundSequencer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 
  交易所 WebSocket 指令處理器
  負責 JSON 解析並注入到定序器 (Sequencer)
 */
@Component
public class ExchangeWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ExchangeWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChronicleQueue gwQueue;

    public ExchangeWebSocketHandler() {
        this.gwQueue = SingleChronicleQueueBuilder.binary("data/spot_exchange/gw-queue").build();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String op = node.get("op").asText();

        // 寫入 Gateway WAL
        gwQueue.acquireAppender().writeDocument(wire -> {
            wire.write("timestamp").int64(System.currentTimeMillis());
            
            if ("auth".equals(op)) {
                String userId = node.get("args").get("userId").asText();
                userSessions.put(userId, session);
                wire.write("msgType").int32(103);
                wire.write("userId").int64(Long.parseLong(userId));
            } else if ("order.create".equals(op)) {
                wire.write("msgType").int32(100);
                wire.write("userId").int64(Long.parseLong(node.get("params").get("userId").asText()));
                wire.write("symbolId").int32(node.get("params").get("symbolId").asInt());
                wire.write("price").int64(node.get("params").get("price").asLong());
                wire.write("qty").int64(node.get("params").get("qty").asLong());
                wire.write("side").int8((byte)node.get("params").get("side").asInt());
            }
        });
    }

    public void sendMessage(String userId, String json) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(json));
            } catch (Exception e) {
                log.error("推送訊息失敗: {}", userId, e);
            }
        }
    }
}
