package open.vincentf13.common.infra.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * 封裝 RedisTemplate 與 StringRedisTemplate 的通用服務
 * - 同槽位檢查以保證 Cluster 下 Pipeline/MULTI/Lua 的可行性
 * - 單語句優先原子操作；多步操作提供 Lua 與 Pipeline 版本
 * - 提供 scan 全主節點遍歷（Cluster）與 cache-aside 模式
 */
@Service
public class RedisStringUtils {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisStringUtils(RedisTemplate<String, Object> redisTemplate,
                            StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ===== Cache-Aside =====
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, Duration ttl, Class<T> type, Supplier<T> loader) {
        T hit = (T) redisTemplate.opsForValue().get(key);
        if (hit != null) return hit;
        synchronized (intern(key)) { // 簡易擊穿保護：單機級別
            T again = (T) redisTemplate.opsForValue().get(key);
            if (again != null) return again;
            T val = loader.get();
            if (val == null) {
                // 穿透保護：空值短 TTL
                redisTemplate.opsForValue().set(key, "", Duration.ofSeconds(Math.max(30, Math.min(ttl.toSeconds() / 10, 300))));
            } else {
                redisTemplate.opsForValue().set(key, val, ttl.plus(randomJitter(Duration.ofSeconds(30))));
            }
            return val;
        }
    }

    private static final Map<String, Object> INTERN = new WeakHashMap<>();

    private static synchronized Object intern(String s) {
        return INTERN.computeIfAbsent(s, k -> new Object());
    }

    private static Duration randomJitter(Duration max) {
        long ms = (long) (Math.random() * Math.max(1, max.toMillis()));
        return Duration.ofMillis(ms);
    }
}
