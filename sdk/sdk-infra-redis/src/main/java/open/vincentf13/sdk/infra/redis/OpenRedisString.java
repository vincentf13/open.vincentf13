package open.vincentf13.sdk.infra.redis;

import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

/**
 * 封裝 RedisTemplate 與 StringRedisTemplate 的通用服務
 * - 同槽位檢查以保證 Cluster 下 Pipeline/MULTI/Lua 的可行性
 * - 單語句優先原子操作；多步操作提供 Lua 與 Pipeline 版本
 * - 提供 scan 全主節點遍歷（Cluster）與 cache-aside 模式
 */

public final class OpenRedisString {

    private static final OpenRedisString INSTANCE = new OpenRedisString();

    private static RedisTemplate<String, Object> redisTemplate;
    private static StringRedisTemplate stringRedisTemplate;

    private OpenRedisString() {
    }

    /*
     * 取得值並轉為指定型別。
     * 例如：
     *   Human human = OpenRedisString.get("human:1", Human.class);
     */
    public static <T> T get(String key, Class<T> targetType) {
        Object obj = redisTemplate().opsForValue().get(key);
        return OpenObjectMapper.convert(obj, targetType);
    }

    public static void register(RedisTemplate<String, Object> redisTemplate,
                                StringRedisTemplate stringRedisTemplate) {
        OpenRedisString.redisTemplate = redisTemplate;
        OpenRedisString.stringRedisTemplate = stringRedisTemplate;
    }

    public static OpenRedisString getInstance() {
        return INSTANCE;
    }


    // ===== Cache-Aside =====
    /*
     * Cache-aside：命中則回傳，未命中載入並回填。
     * 例如：
     *   User user = OpenRedisString.getOrLoad("user:1", Duration.ofMinutes(5), User.class, () -> userRepo.findById(1L));
     */
    public static <T> T getOrLoad(String key, Duration ttl, Class<T> type, Supplier<T> loader) {
        RedisTemplate<String, Object> redis = redisTemplate();
        Object cached = redis.opsForValue().get(key);
        if (cached != null) {
            return OpenObjectMapper.convert(cached, type);
        }
        synchronized (intern(key)) { // 簡易擊穿保護：單機級別
            Object again = redis.opsForValue().get(key);
            if (again != null) {
                return OpenObjectMapper.convert(again, type);
            }
            T val = loader.get();
            if (val == null) {
                // 穿透保護：空值短 TTL
                setAsyncFireAndForget(key, "", Duration.ofSeconds(Math.max(30, Math.min(ttl.toSeconds() / 10, 300))));
            } else {
                setAsyncFireAndForget(key, val, ttl.plus(randomJitter(Duration.ofSeconds(30))));
            }
            return val;
        }
    }

    /*
     * 非阻塞寫入，有重試機制。
     * 例如：
     *   OpenRedisString.setAsync("user:1", user, Duration.ofMinutes(10), 3);
     */
    public static CompletableFuture<Boolean> setAsync(String key, Object value, Duration ttl, int retry) {
        return CompletableFuture.supplyAsync(() -> {
            StringRedisTemplate stringRedis = stringRedisTemplate();
            for (int i = 0; i <= retry; i++) {
                try {
                    stringRedis.opsForValue().set(key, OpenObjectMapper.toJson(value), ttl);
                    return true;
                } catch (Exception e) {
                    if (i == retry)
                        throw new RuntimeException("Redis async set failed", e);
                    try {
                        Thread.sleep(100L * (i + 1));
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            return false;
        }, ForkJoinPool.commonPool());
    }

    /*
     * fire-and-forget 模式，不關心結果。
     * 例如：
     *   OpenRedisString.setAsyncFireAndForget("cache:foo", payload, Duration.ofSeconds(30));
     */
    public static void setAsyncFireAndForget(String key, Object value, Duration ttl) {
        StringRedisTemplate stringRedis = stringRedisTemplate();
        ForkJoinPool.commonPool().execute(() -> {
            try {
                stringRedis.opsForValue().set(key, OpenObjectMapper.toJson(value), ttl);
            } catch (Exception ignored) {
                // 可選擇 log.warn("Redis async set failed", e)
            }
        });
    }

    private static final Map<String, Object> INTERN = new WeakHashMap<>();

    private static synchronized Object intern(String s) {
        return INTERN.computeIfAbsent(s, k -> new Object());
    }

    private static Duration randomJitter(Duration max) {
        long ms = (long) (Math.random() * Math.max(1, max.toMillis()));
        return Duration.ofMillis(ms);
    }

    private static RedisTemplate<String, Object> redisTemplate() {
        if (redisTemplate == null) {
            throw new IllegalStateException("OpenRedisString not initialized");
        }
        return redisTemplate;
    }

    private static StringRedisTemplate stringRedisTemplate() {
        if (stringRedisTemplate == null) {
            throw new IllegalStateException("OpenRedisString not initialized");
        }
        return stringRedisTemplate;
    }
}
