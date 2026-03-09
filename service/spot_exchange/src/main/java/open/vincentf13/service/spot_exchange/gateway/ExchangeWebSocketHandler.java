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
import open.vincentf13.service.spot_exchange.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot_exchange.sbe.MessageHeaderEncoder;
import open.vincentf13.service.spot_exchange.sbe.Side;

@Component
public class ExchangeWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ExchangeWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChronicleQueue gwQueue;
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final OrderCreateEncoder orderCreateEncoder = new OrderCreateEncoder();
    private final UnsafeBuffer tempBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));

    public ExchangeWebSocketHandler() {
        this.gwQueue = SingleChronicleQueueBuilder.binary("data/spot_exchange/gw-queue").build();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String op = node.get("op").asText();

        if ("order.create".equals(op)) {
            // 使用 SBE Encoder
            orderCreateEncoder.wrapAndApplyHeader(tempBuffer, 0, headerEncoder);
            orderCreateEncoder.timestamp(System.currentTimeMillis())
                              .userId(node.get("params").get("userId").asLong())
                              .symbolId(node.get("params").get("symbolId").asLong())
                              .price(node.get("params").get("price").asLong())
                              .qty(node.get("params").get("qty").asLong())
                              .side(Side.get(node.get("params").get("side").asInt()))
                              .clientOrderId(node.get("cid").asText());

            int encodedLength = MessageHeaderEncoder.ENCODED_LENGTH + orderCreateEncoder.encodedLength();

            gwQueue.acquireAppender().writeDocument(wire -> {
                wire.write("msgType").int32(orderCreateEncoder.sbeTemplateId());
                wire.write("payload").bytes(tempBuffer.byteArray(), 0, encodedLength);
            });
        }
        // ... 其他 OP 處理
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
