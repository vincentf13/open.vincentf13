package open.vincentf13.service.spot.ws.ws;

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
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.alloc.NativeUnsafeBuffer;
import open.vincentf13.service.spot.ws.util.JsonUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.sbe.OrderCreateEncoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    
    /**
     Netty Channel 屬性：直接綁定 UserID，規避 SessionToUser Map
     */
    private static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");
    
    /**
     條帶數量 (必須為 2 的冪次以優化取模運算)
     */
    private static final int STRIPE_COUNT = 64;
    private static final int STRIPE_MASK = STRIPE_COUNT - 1;
    
    /**
     條帶化存儲：將用戶分佈在多個獨立的 Map 中以降低鎖競爭
     */
    @SuppressWarnings("unchecked")
    private final Long2ObjectHashMap<Set<Channel>>[] userToSessionsStripes = new Long2ObjectHashMap[STRIPE_COUNT];
    private final Object[] locks = new Object[STRIPE_COUNT];
    
    public WsHandler() {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            userToSessionsStripes[i] = new Long2ObjectHashMap<>(32, 0.6f);
            locks[i] = new Object();
        }
    }
    
    /**
     高效 Hash 算法：混合 userId 高低位以確保分佈均勻
     */
    private int getStripeIndex(long userId) {
        return (int) ((userId ^ (userId >>> 32)) & STRIPE_MASK);
    }
    
    private static final ByteBuf PONG_BUF = Unpooled.unreleasableBuffer(
            Unpooled.copiedBuffer(Ws.PONG, StandardCharsets.UTF_8));
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                TextWebSocketFrame frame) {
        ThreadContext.RequestHolder holder = ThreadContext.get().getRequestHolder();
        holder.reset();
        
        try (JsonParser parser = JsonUtil.createParser(frame.content())) {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.getCurrentName();
                if (field == null)
                    continue;
                parser.nextToken();
                switch (field) {
                    case Ws.OP -> holder.setOp(parser.getText());
                    case Ws.USER_ID -> holder.setUserId(parser.getLongValue());
                    case Ws.ORDER_ID -> holder.setOrderId(parser.getLongValue());
                    case Ws.SYMBOL_ID -> holder.setSymbolId(parser.getIntValue());
                    case Ws.PRICE -> holder.setPrice(parser.getLongValue());
                    case Ws.QTY -> holder.setQty(parser.getLongValue());
                    case Ws.SIDE -> holder.setSide(parser.getText());
                    case Ws.CID -> holder.setCid(parser.getLongValue());
                    case Ws.ASSET_ID -> holder.setAssetId(parser.getIntValue());
                    case Ws.AMOUNT -> holder.setAmount(parser.getLongValue());
                }
            }
            
            if (Ws.PING.equals(holder.getOp())) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame(PONG_BUF.duplicate()));
            } else if (Ws.AUTH.equals(holder.getOp())) {
                handleAuth(ctx.channel(), holder);
            } else if (Ws.CREATE.equals(holder.getOp())) {
                handleOrderCreate(ctx.channel(), holder);
            } else if (Ws.CANCEL.equals(holder.getOp())) {
                handleOrderCancel(ctx.channel(), holder);
            } else if (Ws.DEPOSIT.equals(holder.getOp())) {
                handleDeposit(ctx.channel(), holder);
            }
        } catch (Exception e) {
            log.error("指令解析失敗: {}", e.getMessage());
        }
    }
    
    private void handleAuth(Channel channel,
                            ThreadContext.RequestHolder holder) {
        // 直接將 UserID 綁定到 Channel 屬性，消除 SessionToUser Map
        channel.attr(USER_ID_KEY).set(holder.getUserId());
        
        int idx = getStripeIndex(holder.getUserId());
        synchronized (locks[idx]) {
            userToSessionsStripes[idx].computeIfAbsent(holder.getUserId(), k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                                      .add(channel);
        }
        
        try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.AUTH);
            dc.wire().write(ChronicleWireKey.userId).int64(holder.getUserId());
        }
    }
    
    private void handleOrderCancel(Channel channel,
                                   ThreadContext.RequestHolder holder) {
        Long uid = channel.attr(USER_ID_KEY).get();
        if (uid == null)
            return;
        
        try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.ORDER_CANCEL);
            dc.wire().write(ChronicleWireKey.userId).int64(uid);
            dc.wire().write(ChronicleWireKey.data).int64(holder.getOrderId());
        }
    }
    
    private void handleDeposit(Channel channel,
                               ThreadContext.RequestHolder holder) {
        Long uid = channel.attr(USER_ID_KEY).get();
        if (uid == null)
            return;
        
        try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.DEPOSIT);
            dc.wire().write(ChronicleWireKey.userId).int64(uid);
            dc.wire().write(ChronicleWireKey.assetId).int32(holder.getAssetId());
            dc.wire().write(ChronicleWireKey.data).int64(holder.getAmount());
        }
    }
    
    private void handleOrderCreate(Channel channel,
                                   ThreadContext.RequestHolder holder) {
        Long uid = channel.attr(USER_ID_KEY).get();
        if (uid == null)
            return;

        NativeUnsafeBuffer scratchBuffer = ThreadContext.get().getScratchBuffer();
        scratchBuffer.clear();
        UnsafeBuffer sbeBuffer = scratchBuffer.wrapForWrite();

        int sbeLen = SbeCodec.encode(sbeBuffer, 0, ThreadContext.get().getOrderCreateEncoder()
                                                           .userId(uid).symbolId(holder.getSymbolId()).price(holder.getPrice())
                                                           .qty(holder.getQty()).side(Side.valueOf(holder.getSide())).clientOrderId(holder.getCid())
                                                           .timestamp(System.currentTimeMillis()));
        scratchBuffer.bytes().writePosition(sbeLen);
        
        try (DocumentContext dc = clientToGwWal.acquireAppender().writingDocument()) {
            dc.wire().write(ChronicleWireKey.msgType).int32(MsgType.ORDER_CREATE);
            dc.wire().write(ChronicleWireKey.payload).bytes(scratchBuffer.bytes());
        }
    }
    
    /**
     精準推送：向該用戶的所有活躍設備推送訊息
     */
    public void sendMessage(long userId,
                            ByteBuf data) {
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
                    if (channels.isEmpty())
                        userToSessionsStripes[idx].remove(uid);
                }
            }
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,
                                Throwable cause) {
        ctx.close();
    }
}
