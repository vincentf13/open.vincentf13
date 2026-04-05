package open.vincentf13.service.spot.ws.ws;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.agrona.collections.Long2ObjectHashMap;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 會話管理器 (WsSessionManager)
 * 職責：管理用戶與 Channel 映射，執行條帶化鎖優化的精準推送。
 *
 * 儲存策略：每用戶條目是 Channel 或 Set<Channel>
 * - 單連線（壓測/大多數用戶）：直接存 Channel 物件，熱路徑 zero iterator allocation
 * - 多連線（多裝置）：存 ConcurrentHashMap-backed Set<Channel>，支援 weakly-consistent 無鎖迭代
 */
@Slf4j
@Component
public class WsSessionManager {
    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");
    private static final int STRIPE_COUNT = 64, STRIPE_MASK = 63;

    // Object = Channel | Set<Channel>
    @SuppressWarnings("unchecked")
    private final Long2ObjectHashMap<Object>[] stripes = new Long2ObjectHashMap[STRIPE_COUNT];
    private final Object[] locks = new Object[STRIPE_COUNT];

    public WsSessionManager() {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes[i] = new Long2ObjectHashMap<>(32, 0.6f);
            locks[i] = new Object();
        }
    }

    private int getIdx(long userId) { return (int) ((userId ^ (userId >>> 32)) & STRIPE_MASK); }

    @SuppressWarnings("unchecked")
    public void addSession(long uid, Channel c) {
        Long previousUid = c.attr(USER_ID_KEY).get();
        if (previousUid != null && previousUid != uid) {
            removeSession(previousUid, c);
        }
        c.attr(USER_ID_KEY).set(uid);
        int i = getIdx(uid);
        synchronized (locks[i]) {
            Object existing = stripes[i].get(uid);
            if (existing == null) {
                stripes[i].put(uid, c);                         // 首次加入：存 Channel
            } else if (existing instanceof Channel first) {
                if (first == c) return;                          // 已加入，跳過
                Set<Channel> set = Collections.newSetFromMap(new ConcurrentHashMap<>());
                set.add(first);
                set.add(c);
                stripes[i].put(uid, set);                        // 升級為 Set
            } else {
                ((Set<Channel>) existing).add(c);                // 已經是 Set
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void removeSession(long uid, Channel c) {
        int i = getIdx(uid);
        synchronized (locks[i]) {
            Object existing = stripes[i].get(uid);
            if (existing == null) return;
            if (existing instanceof Channel first) {
                if (first == c) stripes[i].remove(uid);
            } else {
                Set<Channel> set = (Set<Channel>) existing;
                if (set.remove(c)) {
                    if (set.isEmpty()) stripes[i].remove(uid);
                    else if (set.size() == 1) {
                        // 降級為單 Channel：省下後續所有 iterator 分配
                        Channel only = set.iterator().next();
                        stripes[i].put(uid, only);
                    }
                }
            }
        }
        c.attr(USER_ID_KEY).set(null);
    }

    public Long getUserIdByChannel(Channel c) { return c.attr(USER_ID_KEY).get(); }

    /**
     * 向用戶的所有連線佇列二進制訊息（不 flush）。
     * 呼叫端應蒐集回傳的 Channel，批次結束後統一 flush 降低 syscall。
     *
     * @return 單 Channel 時回傳該 Channel；多 Channel 或失敗時已自行 writeAndFlush，回傳 null
     */
    @SuppressWarnings("unchecked")
    public Channel queueMessage(long uid, ByteBuf data) {
        int i = getIdx(uid);
        Object target;
        synchronized (locks[i]) { target = stripes[i].get(uid); }
        if (target == null) { data.release(); return null; }

        // Fast path：單 Channel，write 不 flush，交由呼叫端批次 flush
        if (target instanceof Channel c) {
            if (c.isActive()) {
                c.write(new BinaryWebSocketFrame(data));
                return c;
            }
            data.release();
            removeSession(uid, c);
            return null;
        }

        // 多連線路徑：各 channel 直接 writeAndFlush（目前不做跨用戶批次）
        Set<Channel> channels = (Set<Channel>) target;
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(data);
        try {
            for (Channel c : channels) {
                if (c.isActive()) c.writeAndFlush(frame.retainedDuplicate());
                else removeSession(uid, c);
            }
        } catch (Exception e) {
            log.error("[WS] Push failed for {}: {}", uid, e.getMessage());
        } finally {
            frame.release();
        }
        return null;
    }
}
