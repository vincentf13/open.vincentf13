package open.vincentf13.service.spot.gateway.ws;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.gateway.util.JsonUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot.sbe.Side;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 WebSocket 接入處理器 (WsHandler)
 職責：管理連線、零分配解析指令並執行 $O(1)$ 的精準推送，支援多設備同時在線
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private final ChronicleQueue clientToGwWal = Storage.self().clientToGwWal();
    
    /** Netty Channel 屬性：直接綁定 UserID，規避 SessionToUser Map */
    private static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");
    
    /** 條帶數量 (必須為 2 的冪次以優化取模運算) */
    private static final int STRIPE_COUNT = 64;
    private static final int STRIPE_MASK = STRIPE_COUNT - 1;
    
    /** 條帶化存儲：將用戶分佈在多個獨立的 Map 中以降低鎖競爭 */
    @SuppressWarnings("unchecked")
    private final Long2ObjectHashMap<Set<Channel>>[] userToSessionsStripes = new Long2ObjectHashMap[STRIPE_COUNT];
    private final Object[] locks = new Object[STRIPE_COUNT];

    public WsHandler() {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            userToSessionsStripes[i] = new Long2ObjectHashMap<>(32, 0.6f);
            locks[i] = new Object();
        }
    }
    
    /** 高效 Hash 算法：混合 userId 高低位以確保分佈均勻 */
    private int getStripeIndex(long userId) {
        return (int) ((userId ^ (userId >>> 32)) & STRIPE_MASK);
    }
    
    private final OrderCreateEncoder orderCreateEncoder = new OrderCreateEncoder();
    
    /** ThreadLocal 緩衝區：消除 synchronized 競爭，實現全並發 SBE 編碼 */
    private static final ThreadLocal<Bytes<ByteBuffer>> SBE_BYTES_THREAD_LOCAL = ThreadLocal.withInitial(() -> Bytes.elasticByteBuffer(512));
    private static final ThreadLocal<UnsafeBuffer> SBE_BUFFER_THREAD_LOCAL = ThreadLocal.withInitial(() -> new UnsafeBuffer(0, 0));

    private static final ByteBuf PONG_BUF = Unpooled.unreleasableBuffer(
            Unpooled.copiedBuffer(Ws.PONG, StandardCharsets.UTF_8));

    @Data
    private static class RequestHolder {
        String op; long userId; long orderId; int symbolId; long price; long qty; String side; long cid;
        int assetId; long amount;
        void reset() { op = null; userId = 0; orderId = 0; symbolId = 0; price = 0; qty = 0; side = null; cid = 0; assetId = 0; amount = 0; }
    }

    private final ThreadLocal<RequestHolder> holderPool = ThreadLocal.withInitial(RequestHolder::new);

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        RequestHolder holder = holderPool.get();
        holder.reset();

        try (JsonParser parser = JsonUtil.createParser(frame.content())) {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.getCurrentName();
                if (field == null) continue;
                parser.nextToken();
                switch (field) {
                    case Ws.OP -> holder.op = parser.getText();
                    case Ws.USER_ID -> holder.userId = parser.getLongValue();
                    case Ws.ORDER_ID -> holder.orderId = parser.getLongValue();
                    case Ws.SYMBOL_ID -> holder.symbolId = parser.getIntValue();
                    case Ws.PRICE -> holder.price = parser.getLongValue();
                    case Ws.QTY -> holder.qty = parser.getLongValue();
                    case Ws.SIDE -> holder.side = parser.getText();
                    case Ws.CID -> holder.cid = parser.getLongValue();
                    case Ws.ASSET_ID -> holder.assetId = parser.getIntValue();
                    case Ws.AMOUNT -> holder.amount = parser.getLongValue();
                }
            }

            if (Ws.PING.equals(holder.op)) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame(PONG_BUF.duplicate()));
            } else if (Ws.AUTH.equals(holder.op)) {
                handleAuth(ctx.channel(), holder);
            } else if (Ws.CREATE.equals(holder.op)) {
                handleOrderCreate(ctx.channel(), holder);
            } else if (Ws.CANCEL.equals(holder.op)) {
                handleOrderCancel(ctx.channel(), holder);
            } else if (Ws.DEPOSIT.equals(holder.op)) {
                handleDeposit(ctx.channel(), holder);
            }
        } catch (Exception e) {
            log.error("指令解析失敗: {}", e.getMessage());
        }
    }

    private void handleAuth(Channel channel, RequestHolder holder) {
        // 直接將 UserID 綁定到 Channel 屬性，消除 SessionToUser Map
        channel.attr(USER_ID_KEY).set(holder.userId);
        
        int idx = getStripeIndex(holder.userId);
        synchronized (locks[idx]) {
            userToSessionsStripes[idx].computeIfAbsent(holder.userId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                    .add(channel);
        }
        
        try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.AUTH);
            dc.wire().write(ChronicleWireKey.userId).int64(holder.userId);
        }
    }

    private void handleOrderCancel(Channel channel, RequestHolder holder) {
        Long uid = channel.attr(USER_ID_KEY).get();
        if (uid == null) return;
        
        try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.ORDER_CANCEL);
            dc.wire().write(ChronicleWireKey.userId).int64(uid);
            dc.wire().write(ChronicleWireKey.data).int64(holder.orderId);
        }
    }

    private void handleDeposit(Channel channel, RequestHolder holder) {
        Long uid = channel.attr(USER_ID_KEY).get();
        if (uid == null) return;
        
        try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.DEPOSIT);
            dc.wire().write(ChronicleWireKey.userId).int64(uid);
            dc.wire().write(ChronicleWireKey.assetId).int32(holder.assetId);
            dc.wire().write(ChronicleWireKey.data).int64(holder.amount);
        }
    }

    private void handleOrderCreate(Channel channel, RequestHolder holder) {
        Long uid = channel.attr(USER_ID_KEY).get();
        if (uid == null) return;

        Bytes<ByteBuffer> sbeBytes = SBE_BYTES_THREAD_LOCAL.get();
        UnsafeBuffer sbeBuffer = SBE_BUFFER_THREAD_LOCAL.get();
        
        sbeBytes.clear();
        sbeBuffer.wrap(sbeBytes.addressForWrite(0), (int) sbeBytes.realCapacity());
        int sbeLen = SbeCodec.encode(sbeBuffer, 0, orderCreateEncoder
            .userId(uid).symbolId(holder.symbolId).price(holder.price)
            .qty(holder.qty).side(Side.valueOf(holder.side)).clientOrderId(holder.cid)
            .timestamp(System.currentTimeMillis()));
        sbeBytes.writePosition(sbeLen);

        try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.ORDER_CREATE);
            dc.wire().write(ChronicleWireKey.payload).bytes(sbeBytes);
        }
    }

    /** 
      精準推送：向該用戶的所有活躍設備推送訊息
     */
    public void sendMessage(long userId, ByteBuf data) {
        Set<Channel> channels;
        int idx = getStripeIndex(userId);
        synchronized (locks[idx]) {
            channels = userToSessionsStripes[idx].get(userId);
        }
        
        if (channels == null || channels.isEmpty()) {
            data.release();
            return;
        }

        try {
            for (Channel c : channels) {
                if (c.isActive()) {
                    c.writeAndFlush(new TextWebSocketFrame(data.retain()));
                }
            }
        } finally {
            data.release();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long uid = ctx.channel().attr(USER_ID_KEY).get();
        if (uid != null) {
            int idx = getStripeIndex(uid);
            synchronized (locks[idx]) {
                Set<Channel> channels = userToSessionsStripes[idx].get(uid);
                if (channels != null) {
                    channels.remove(ctx.channel());
                    if (channels.isEmpty()) userToSessionsStripes[idx].remove(uid);
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
