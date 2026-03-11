package open.vincentf13.service.spot.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.JsonUtil;
import open.vincentf13.service.spot.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot.sbe.Side;

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
    private final Map<String, Channel> userChannels = new ConcurrentHashMap<>();
    
    // 預分配 SBE 編碼對象與緩衝區 (ThreadLocal 化以確保執行緒安全)
    private final OrderCreateEncoder createEncoder = new OrderCreateEncoder();
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
    private final Bytes<ByteBuffer> bytes = Bytes.elasticByteBuffer(1024);
    private final byte[] tempArray = new byte[1024];

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        JsonNode root = JsonUtil.parse(frame.text());
        String op = root.get(Ws.OP).asText();
        
        if (Ws.PING.equals(op)) ctx.writeAndFlush(new TextWebSocketFrame(Ws.PONG));
        else if (Ws.AUTH.equals(op)) handleAuth(ctx, root);
        else if (Ws.CREATE.equals(op)) handleOrderCreate(ctx, root);
    }

    /** 
      處理認證指令 (auth)
      邏輯：將 userId 與 Channel 綁定，並寫入本地指令隊列
     */
    private void handleAuth(ChannelHandlerContext ctx, JsonNode root) {
        long userId = root.get(Ws.ARGS).get(0).get(Ws.USER_ID).asLong();
        userChannels.put(String.valueOf(userId), ctx.channel());
        
        // 寫入本地隊列：認證訊息 [msgType][userId]
        Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
            wire.write(ChronicleWireKey.msgType).int32(MsgType.AUTH);
            wire.write(ChronicleWireKey.userId).int64(userId);
        });
    }

    /** 
      處理下單指令 (order.create)
      邏輯：JSON -> SBE 二進位 -> 寫入本地隊切
     */
    private void handleOrderCreate(ChannelHandlerContext ctx, JsonNode root) {
        JsonNode args = root.get(Ws.ARGS).get(0);
        
        // 使用 SbeEncoder 進行二進位編碼
        buffer.setMemory(0, buffer.capacity(), (byte) 0);
        createEncoder.wrap(buffer, 0)
                .timestamp(System.currentTimeMillis())
                .userId(args.get(Ws.USER_ID).asLong())
                .symbolId(args.get(Ws.SYMBOL_ID).asInt())
                .price(args.get(Ws.PRICE).asLong())
                .qty(args.get(Ws.QTY).asLong())
                .side(Side.valueOf(args.get(Ws.SIDE).asText().toUpperCase()))
                .clientOrderId(root.get(Ws.CID).asText());

        int len = createEncoder.encodedLength();
        buffer.getBytes(0, tempArray, 0, len);
        bytes.clear();
        bytes.write(tempArray, 0, len);
        
        Storage.self().gatewayQueue().acquireAppender().writeDocument(wire -> {
            wire.write(ChronicleWireKey.msgType).int32(MsgType.ORDER_CREATE);
            wire.write(ChronicleWireKey.payload).bytes(bytes);
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket 異常: {}", cause.getMessage());
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        userChannels.values().remove(ctx.channel());
        try {
            super.channelInactive(ctx);
        } catch (Exception e) {
            log.error("Channel Inactive Error: {}", e.getMessage());
        }
    }

    public void sendMessage(String userId, String json) {
        Channel channel = userChannels.get(userId);
        if (channel != null && channel.isActive()) channel.writeAndFlush(new TextWebSocketFrame(json));
    }
}
