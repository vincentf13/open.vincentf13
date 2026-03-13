package open.vincentf13.service.spot.ws.ws;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.agrona.collections.Long2ObjectHashMap;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 會話管理器 (WsSessionManager)
 * 職責：管理用戶與 Channel 的映射關係，執行條帶化鎖優化的精準推送
 */
@Slf4j
@Component
public class WsSessionManager {
    /**
     * Netty Channel 屬性：直接綁定 UserID，規避 SessionToUser Map
     */
    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");

    /**
     * 條帶數量 (必須為 2 的冪次以優化取模運算)
     */
    private static final int STRIPE_COUNT = 64;
    private static final int STRIPE_MASK = STRIPE_COUNT - 1;

    /**
     * 條帶化存儲：將用戶分佈在多個獨立的 Map 中以降低鎖競爭
     */
    @SuppressWarnings("unchecked")
    private final Long2ObjectHashMap<Set<Channel>>[] userToSessionsStripes = new Long2ObjectHashMap[STRIPE_COUNT];
    private final Object[] locks = new Object[STRIPE_COUNT];

    public WsSessionManager() {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            userToSessionsStripes[i] = new Long2ObjectHashMap<>(32, 0.6f);
            locks[i] = new Object();
        }
    }

    /**
     * 高效 Hash 算法：混合 userId 高低位以確保分佈均勻
     */
    private int getStripeIndex(long userId) {
        return (int) ((userId ^ (userId >>> 32)) & STRIPE_MASK);
    }

    public void addSession(long userId, Channel channel) {
        channel.attr(USER_ID_KEY).set(userId);
        int idx = getStripeIndex(userId);
        synchronized (locks[idx]) {
            userToSessionsStripes[idx].computeIfAbsent(userId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                    .add(channel);
        }
    }

    public void removeSession(long userId, Channel channel) {
        int idx = getStripeIndex(userId);
        synchronized (locks[idx]) {
            Set<Channel> channels = userToSessionsStripes[idx].get(userId);
            if (channels != null) {
                channels.remove(channel);
                if (channels.isEmpty()) {
                    userToSessionsStripes[idx].remove(userId);
                }
            }
        }
    }

    public Long getUserIdByChannel(Channel channel) {
        return channel.attr(USER_ID_KEY).get();
    }

    /**
     * 精準推送：向該用戶的所有活躍設備推送訊息
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
}
