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
        // 從重用物件中提取參數 (避免在 Heap 產生臨時物件)
        OrderRequest.Params p = req.getParams();
        // 堆外內存緩衝區
        Bytes<ByteBuffer> bytes = bytesCache.get(); bytes.clear();
        // 內存裸寫: 用於繞過 JVM 邊界檢查直接操作內存
        UnsafeBuffer buffer = bufferCache.get();
        // CPU 快取友善 : 將 UnsafeBuffer 鎖定在 Bytes 物件的起始物理地址上
        // 這樣 SBE 編碼器寫入數據時，就是直接在操作那塊堆外記憶體，效率接近 C 語言的 Load/Store
        buffer.wrap(bytes.addressForWrite(0), (int) bytes.realCapacity());
        OrderCreateEncoder encoder = orderEncoder.get();
        
        // 執行 SBE 二進制編碼：
        // 這一步是 O(1) 操作，按順序將數據填入緩衝區的固定位置 (如 offset + 8 寫 userId)
        // 返回值 len 是這次下單指令編碼後的總位元組長度
        int len = SbeCodec.encode(buffer, 0, encoder.timestamp(System.currentTimeMillis())
                                                    .userId(p.getUserId()).symbolId(p.getSymbolId())
                                                    .price(p.getPrice()).qty(p.getQty())
                                                    .side(Side.get((short) p.getSide())).clientOrderId(req.getCid()));
        // 同步指標：告訴 Bytes 對象，現在有效數據已經寫到了 len 的位置
        // 若不寫這行，下游組件會認為緩衝區是空的
        bytes.writePosition(len);
        
        // 寫入本地隊列 WAL (Write-Ahead Log)：利用 mmap 技術將內存數據持久化到磁碟
        //  這是為了保證數據不丟失，並供後續的 Aeron 發送器異步讀取發往撮合引擎
        Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
            wire.write(Fields.msgType).int32(encoder.sbeTemplateId());
            wire.write(Fields.payload).bytes(bytes);
        });
    }

    private void handleAuth(Channel channel, JsonNode root) {
        long userId = root.path(Ws.ARGS).path(Ws.USER_ID).asLong(0);
        if (userId == 0) return;

        String uidStr = String.valueOf(userId);
        userChannels.put(uidStr, channel);
        channel.attr(ATTR_USER_ID).set(uidStr); // 在 Channel 上貼標籤，實現 $O(1)$ 的斷線清理

        Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
            wire.write(Fields.msgType).int32(Msg.AUTH);
            wire.write(Fields.userId).int64(userId);
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
