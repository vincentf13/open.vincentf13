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
    private final InboundSequencer sequencer;
    
    // 用戶 ID 與 WS 會話的映射
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    public ExchangeWebSocketHandler(InboundSequencer sequencer) {
        this.sequencer = sequencer;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String op = node.get("op").asText();

        // 將所有指令持久化到 Inbound Queue (WAL)
        sequencer.getQueue().acquireAppender().writeDocument(wire -> {
            wire.write("timestamp").int64(System.currentTimeMillis());
            
            if ("auth".equals(op)) {
                String userId = node.get("args").get("userId").asText();
                userSessions.put(userId, session);
                wire.write("msgType").int32(103);
                wire.write("userId").int64(Long.parseLong(userId));
            } else if ("order.create".equals(op)) {
                wire.write("msgType").int32(100);
                // 這裡繼續填入下單參數 (SBE/Wire 轉換)
                wire.write("userId").int64(Long.parseLong(node.get("params").get("userId").asText()));
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
