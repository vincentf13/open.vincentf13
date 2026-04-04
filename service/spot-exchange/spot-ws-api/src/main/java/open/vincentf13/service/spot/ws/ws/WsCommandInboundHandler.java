package open.vincentf13.service.spot.ws.ws;

import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.sbe.*;
import open.vincentf13.service.spot.ws.wal.WalEvent;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 網關 WebSocket 指令處理器
 *
 * Netty worker 解碼 SBE → 填入 WalEvent 結構欄位 → CAS 投遞 Disruptor RingBuffer。
 * SBE 欄位偏移來自 generated encoder，確保 schema 變更時自動同步。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WsCommandInboundHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {
    /** 客戶端幀的自訂標頭長度 (msgType 4 + padding 8 + sbeHeader 8 = 20) */
    private static final int OLD_HEADER_SIZE = 20;
    private static final int MIN_COMMAND_LENGTH = OLD_HEADER_SIZE;
    /** SBE body 在客戶端幀中的起始偏移 (= OLD_HEADER_SIZE) */
    private static final int SBE_BODY_START = OLD_HEADER_SIZE;

    private final RingBuffer<WalEvent> ringBuffer;
    private final WsSessionManager sessionManager;

    public WsCommandInboundHandler(RingBuffer<WalEvent> ringBuffer, WsSessionManager sessionManager) {
        this.ringBuffer = ringBuffer;
        this.sessionManager = sessionManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        final long arrivalTimeNs = System.nanoTime();
        StaticMetricsHolder.addCounter(MetricsKey.NETTY_RECV_COUNT, 1);

        ByteBuf content = frame.content();
        int length = content.readableBytes();
        if (length < MIN_COMMAND_LENGTH) return;

        int ri = content.readerIndex();
        int msgType = content.getIntLE(ri);
        Long existingUserId = ctx.channel().attr(WsSessionManager.USER_ID_KEY).get();

        // CAS 搶佔 RingBuffer slot (無鎖)
        long seq;
        try {
            seq = ringBuffer.tryNext();
        } catch (InsufficientCapacityException e) {
            StaticMetricsHolder.addCounter(MetricsKey.GATEWAY_WAL_DROP_COUNT, 1);
            return;
        }

        long userId = 0;
        try {
            WalEvent event = ringBuffer.get(seq);
            event.msgType = msgType;
            event.arrivalTimeNs = arrivalTimeNs;
            decodeSbeBody(content, ri, msgType, event);
            userId = event.userId;
        } finally {
            ringBuffer.publish(seq);
        }

        // AUTH 會話綁定 (publish 後使用已擷取的 userId，不再觸碰 event)
        if (existingUserId == null && msgType == MsgType.AUTH && userId > 0) {
            sessionManager.addSession(userId, ctx.channel());
        }
    }

    /**
     * 從客戶端 ByteBuf 解碼 SBE body 欄位至 WalEvent 結構。
     * 偏移常數來自 SBE generated encoder，保證與 schema 一致。
     */
    private static void decodeSbeBody(ByteBuf buf, int ri, int msgType, WalEvent e) {
        int b = ri + SBE_BODY_START; // SBE body fields 起始位址

        switch (msgType) {
            case MsgType.ORDER_CREATE -> {
                e.timestamp = buf.getLongLE(b + OrderCreateEncoder.timestampEncodingOffset());
                e.userId    = buf.getLongLE(b + OrderCreateEncoder.userIdEncodingOffset());
                e.orderCreate.symbolId      = buf.getIntLE(b + OrderCreateEncoder.symbolIdEncodingOffset());
                e.orderCreate.price         = buf.getLongLE(b + OrderCreateEncoder.priceEncodingOffset());
                e.orderCreate.qty           = buf.getLongLE(b + OrderCreateEncoder.qtyEncodingOffset());
                e.orderCreate.side          = buf.getByte(b + OrderCreateEncoder.sideEncodingOffset());
                e.orderCreate.clientOrderId = buf.getLongLE(b + OrderCreateEncoder.clientOrderIdEncodingOffset());
            }
            case MsgType.ORDER_CANCEL -> {
                e.timestamp = buf.getLongLE(b + OrderCancelEncoder.timestampEncodingOffset());
                e.userId    = buf.getLongLE(b + OrderCancelEncoder.userIdEncodingOffset());
                e.orderCancel.orderId = buf.getLongLE(b + OrderCancelEncoder.orderIdEncodingOffset());
            }
            case MsgType.DEPOSIT -> {
                e.timestamp = buf.getLongLE(b + DepositEncoder.timestampEncodingOffset());
                e.userId    = buf.getLongLE(b + DepositEncoder.userIdEncodingOffset());
                e.deposit.assetId = buf.getIntLE(b + DepositEncoder.assetIdEncodingOffset());
                e.deposit.amount  = buf.getLongLE(b + DepositEncoder.amountEncodingOffset());
            }
            case MsgType.AUTH -> {
                e.timestamp = buf.getLongLE(b + AuthEncoder.timestampEncodingOffset());
                e.userId    = buf.getLongLE(b + AuthEncoder.userIdEncodingOffset());
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof BinaryWebSocketFrame) {
            super.channelRead(ctx, msg);
        } else {
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long uid = sessionManager.getUserIdByChannel(ctx.channel());
        if (uid != null) sessionManager.removeSession(uid, ctx.channel());
    }
}
