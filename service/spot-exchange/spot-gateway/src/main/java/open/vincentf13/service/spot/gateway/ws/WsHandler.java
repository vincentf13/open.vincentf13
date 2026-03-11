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
import open.vincentf13.service.spot.model.OrderRequest;
import open.vincentf13.service.spot.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
  WebSocket 指令處理器
  負責：JSON 解析、參數校驗、SBE 編碼、Aeron 轉發
  效能：採用 ThreadLocal 緩衝區與 POJO 重用技術實現全路徑 Zero-GC 指令轉換
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final AttributeKey<String> ATTR_USER_ID = AttributeKey.valueOf(Ws.USER_ID);
    private final Map<String, Channel> userChannels = new ConcurrentHashMap<>();

    // --- Zero-GC 資源池 (ThreadLocal 隔離) ---
    private final ThreadLocal<Bytes<ByteBuffer>> bytesCache = ThreadLocal.withInitial(() -> Bytes.elasticByteBuffer(512));
    private final ThreadLocal<UnsafeBuffer> bufferCache = ThreadLocal.withInitial(() -> new UnsafeBuffer(0, 0));
    private final ThreadLocal<OrderCreateEncoder> orderEncoder = ThreadLocal.withInitial(OrderCreateEncoder::new);
    private final ThreadLocal<OrderRequest> reusableOrderRequest = ThreadLocal.withInitial(OrderRequest::new);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String text = frame.text();
        // 快速判斷 Ping，避開 JSON 解析開銷
        if (text.contains(Ws.PING)) { ctx.channel().writeAndFlush(new TextWebSocketFrame(Ws.PONG)); return; }

        JsonNode root = JsonUtil.readTree(text);
        if (root == null) return;

        String op = root.path(Ws.OP).asText("");
        if (Ws.CREATE.equals(op)) {
            // 使用重用物件執行 Zero-Allocation 解析
            OrderRequest req = reusableOrderRequest.get();
            req.reset();
            JsonUtil.updateObject(text, req);
            handleOrderCreate(ctx.channel(), req);
        } else if (Ws.AUTH.equals(op)) {
            handleAuth(ctx.channel(), root);
        }
    }

    private void handleOrderCreate(Channel channel, OrderRequest req) {
        OrderRequest.Params p = req.getParams();
        Bytes<ByteBuffer> bytes = bytesCache.get(); bytes.clear();
        UnsafeBuffer buffer = bufferCache.get(); buffer.wrap(bytes.addressForWrite(0), (int) bytes.realCapacity());
        OrderCreateEncoder encoder = orderEncoder.get();

        // 執行二進制協議編碼 (SBE)
        int len = SbeCodec.encode(buffer, 0, encoder.timestamp(System.currentTimeMillis())
            .userId(p.getUserId()).symbolId(p.getSymbolId())
            .price(p.getPrice()).qty(p.getQty())
            .side(Side.get((short) p.getSide())).clientOrderId(req.getCid()));

        bytes.writePosition(len);
        
        // 寫入本地隊列 WAL，供 Aeron 發送器異步讀取
        Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
            wire.write(ChronicleWireKey.msgType).int32(encoder.sbeTemplateId());
            wire.write(ChronicleWireKey.payload).bytes(bytes);
        });
    }

    private void handleAuth(Channel channel, JsonNode root) {
        long userId = root.path(Ws.ARGS).path(Ws.USER_ID).asLong(0);
        if (userId == 0) return;

        String uidStr = String.valueOf(userId);
        userChannels.put(uidStr, channel);
        channel.attr(ATTR_USER_ID).set(uidStr); // 在 Channel 上貼標籤，實現 $O(1)$ 的斷線清理

        Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
            wire.write(ChronicleWireKey.msgType).int32(MSG_AUTH);
            wire.write(ChronicleWireKey.userId).int64(userId);
        });
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

    public void sendMessage(String userId, String json) {
        Channel channel = userChannels.get(userId);
        if (channel != null && channel.isActive()) channel.writeAndFlush(new TextWebSocketFrame(json));
    }
}
