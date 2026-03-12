package open.vincentf13.service.spot.gateway.ws;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
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
 職責：管理客戶端連線、解析 JSON 請求並寫入客戶端指令流 (Client -> Gateway)
 */
@Slf4j
@Component
public class WsHandler extends TextWebSocketHandler {
    // 依賴的具體存儲結構 (反映 Client -> Gateway 流向)
    private final ChronicleQueue clientToGwWal = Storage.self().clientToGwWal();

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    
    private final OrderCreateEncoder createEncoder = new OrderCreateEncoder();
    private final Bytes<ByteBuffer> sbeBytes = Bytes.elasticByteBuffer(512);
    private final UnsafeBuffer sbeBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> req = JsonUtil.toMap(message.getPayload());
            String op = (String) req.get(Ws.OP);

            if (Ws.PING.equals(op)) {
                session.sendMessage(new TextMessage(Ws.PONG));
            } else if (Ws.AUTH.equals(op)) {
                handleAuth(session, req);
            } else if (Ws.CREATE.equals(op)) {
                handleOrderCreate(session, req);
            }
        } catch (Exception e) {
            log.error("處理 WS 訊息失敗: {}", e.getMessage());
        }
    }

    private void handleAuth(WebSocketSession session, Map<String, Object> req) {
        long userId = ((Number) req.get(Ws.USER_ID)).longValue();
        sessionToUser.put(session.getId(), String.valueOf(userId));
        
        clientToGwWal.acquireAppender().writeDocument(wire -> {
            wire.write(ChronicleWireKey.msgType).int32(MsgType.AUTH);
            wire.write(ChronicleWireKey.userId).int64(userId);
        });
    }

    private void handleOrderCreate(WebSocketSession session, Map<String, Object> req) {
        String uid = sessionToUser.get(session.getId());
        if (uid == null) return;

        synchronized (sbeBytes) {
            sbeBytes.clear();
            sbeBuffer.wrap(sbeBytes.addressForWrite(0), (int) sbeBytes.realCapacity());
            
            int sbeLen = SbeCodec.encode(sbeBuffer, 0, createEncoder
                .userId(Long.parseLong(uid))
                .symbolId(((Number) req.get(Ws.SYMBOL_ID)).longValue())
                .price(((Number) req.get(Ws.PRICE)).longValue())
                .qty(((Number) req.get(Ws.QTY)).longValue())
                .side(Side.valueOf((String) req.get(Ws.SIDE)))
                .clientOrderId((String) req.get(Ws.CID))
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
            .filter(s -> String.valueOf(userId).equals(sessionToUser.get(s.getId())))
            .forEach(s -> {
                try { s.sendMessage(new TextMessage(json)); } catch (Exception ignored) {}
            });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        sessionToUser.remove(session.getId());
    }
}
