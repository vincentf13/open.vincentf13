package open.vincentf13.service.spot.ws.ws;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.agrona.collections.Long2ObjectHashMap;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 會話管理器 (WsSessionManager)
 * 職責：管理用戶與 Channel 映射，執行條帶化鎖優化的精準推送。
 */
@Slf4j
@Component
public class WsSessionManager {
    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");
    private static final int STRIPE_COUNT = 64, STRIPE_MASK = 63;

    @SuppressWarnings("unchecked")
    private final Long2ObjectHashMap<Set<Channel>>[] stripes = new Long2ObjectHashMap[STRIPE_COUNT];
    private final Object[] locks = new Object[STRIPE_COUNT];

    public WsSessionManager() {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes[i] = new Long2ObjectHashMap<>(32, 0.6f);
            locks[i] = new Object();
        }
    }

    private int getIdx(long userId) { return (int) ((userId ^ (userId >>> 32)) & STRIPE_MASK); }

    public void addSession(long uid, Channel c) {
        c.attr(USER_ID_KEY).set(uid);
        int i = getIdx(uid);
        synchronized (locks[i]) {
            stripes[i].computeIfAbsent(uid, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(c);
        }
    }

    public void removeSession(long uid, Channel c) {
        int i = getIdx(uid);
        synchronized (locks[i]) {
            Set<Channel> set = stripes[i].get(uid);
            if (set != null && set.remove(c) && set.isEmpty()) stripes[i].remove(uid);
        }
    }

    public Long getUserIdByChannel(Channel c) { return c.attr(USER_ID_KEY).get(); }

    /** 向用戶的所有連線廣播二進制訊息 */
    public void sendMessage(long uid, ByteBuf data) {
        Set<Channel> channels;
        int i = getIdx(uid);
        synchronized (locks[i]) { channels = stripes[i].get(uid); }

        if (channels == null || channels.isEmpty()) { data.release(); return; }

        BinaryWebSocketFrame frame = null;
        try {
            frame = new BinaryWebSocketFrame(data);
            for (Channel c : channels) { if (c.isActive()) c.writeAndFlush(frame.retainedDuplicate()); }
        } catch (Exception e) { log.error("[WS] Push failed for {}: {}", uid, e.getMessage()); } 
        finally { if (frame != null) frame.release(); else data.release(); }
    }
}
