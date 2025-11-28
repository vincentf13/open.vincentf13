package open.vincentf13.sdk.infra.redis;

import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

import static open.vincentf13.sdk.infra.redis.RedisUtil.withJitter;

/**
 封裝 RedisTemplate 與 StringRedisTemplate 的通用服務
 - 同槽位檢查以保證 Cluster 下 Pipeline/MULTI/Lua 的可行性
 - 單語句優先原子操作；多步操作提供 Lua 與 Pipeline 版本
 - 提供 scan 全主節點遍歷（Cluster）與 cache-aside 模式
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
    public static <T> T get(String key,
                            Class<T> targetType) {
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
    public static <T> T getOrLoad(String key,
                                  Duration ttl,
                                  Class<T> type,
                                  Supplier<T> loader) {
        RedisTemplate<String, Object> redis = redisTemplate();
        Object cached = redis.opsForValue().get(key);
        if (cached != null) {
            return OpenObjectMapper.convert(cached, type);
        }
        synchronized (RedisUtil.intern(key)) { // 簡易擊穿保護：單機級別
            Object again = redis.opsForValue().get(key);
            if (again != null) {
                return OpenObjectMapper.convert(again, type);
            }
            T val = loader.get();
            if (val == null) {
                // 穿透保護：空值短 TTL
                setAsyncFireAndForget(key, "", withJitter(Duration.ofSeconds(Math.max(30, Math.min(ttl.toSeconds() / 10, 300)))));
            } else {
                setAsyncFireAndForget(key, val, withJitter(ttl));
            }
            return val;
        }
    }
    
    /*
     * 非阻塞寫入，有重試機制。
     * 例如：
     *   OpenRedisString.setAsync("user:1", user, Duration.ofMinutes(10), 3);
     */
    public static CompletableFuture<Boolean> setAsync(String key,
                                                      Object value,
                                                      Duration ttl,
                                                      int retry) {
        Duration ttlWithJitter = withJitter(ttl);
        return CompletableFuture.supplyAsync(() -> {
            StringRedisTemplate stringRedis = stringRedisTemplate();
            for (int i = 0; i <= retry; i++) {
                try {
                    stringRedis.opsForValue().set(key, OpenObjectMapper.toJson(value), ttlWithJitter);
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
     * 批次非阻塞寫入，逐筆帶入 TTL 並加抖動。
     * 例如：
     *   List<KeyValueTtl> batch = List.of(
     *       new KeyValueTtl("user:1", user1, Duration.ofMinutes(5)),
     *       new KeyValueTtl("user:2", user2, Duration.ofMinutes(5))
     *   );
     *   OpenRedisString.setBatch(batch);
     */
    public static void setBatch(Collection<KeyValueTtl> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        StringRedisTemplate stringRedis = stringRedisTemplate();
        SessionCallback<Object> callback = new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                @SuppressWarnings("unchecked")
                RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                entries.stream()
                       .filter(Objects::nonNull)
                       .forEach(entry -> {
                           Duration ttlWithJitter = withJitter(Objects.requireNonNull(entry.ttl(), "ttl is required"));
                           ops.opsForValue().set(Objects.requireNonNull(entry.key(), "key is required"),
                                                 OpenObjectMapper.toJson(entry.value()), ttlWithJitter);
                       });
                return null;
            }
        };
        stringRedis.executePipelined(callback);
    }
    
    /*
     * Cluster 版批次寫入，依槽位分批 pipeline。
     * 例如：
     *   OpenRedisString.setBatchCluster(List.of(new KeyValueTtl("user:1", user1, Duration.ofMinutes(5))));
     */
    public static void setBatchCluster(Collection<KeyValueTtl> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        StringRedisTemplate stringRedis = stringRedisTemplate();
        Map<Integer, Collection<KeyValueTtl>> grouped = new LinkedHashMap<>();
        entries.stream()
               .filter(Objects::nonNull)
               .forEach(entry -> {
                   String key = Objects.requireNonNull(entry.key(), "key is required");
                   grouped.computeIfAbsent(RedisClusterUtil.slot(key), k -> new ArrayList<>()).add(entry);
               });
        grouped.values().forEach(group -> {
            SessionCallback<Object> callback = new SessionCallback<>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) {
                    @SuppressWarnings("unchecked")
                    RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                    group.forEach(entry -> {
                        Duration ttlWithJitter = withJitter(Objects.requireNonNull(entry.ttl(), "ttl is required"));
                        ops.opsForValue().set(entry.key(), OpenObjectMapper.toJson(entry.value()), ttlWithJitter);
                    });
                    return null;
                }
            };
            stringRedis.executePipelined(callback);
        });
    }
    
    /*
     * 批次讀取：所有 key 同型別。
     * 例如：
     *   Map<String, User> users = OpenRedisString.getBatch(List.of("user:1", "user:2"), User.class);
     */
    public static <T> Map<String, T> getBatch(Collection<String> keys,
                                              Class<T> targetType) {
        Objects.requireNonNull(targetType, "type is required");
        Map<String, Class<?>> mapping = new LinkedHashMap<>();
        if (keys != null) {
            keys.forEach(key -> mapping.put(Objects.requireNonNull(key, "key is required"), targetType));
        }
        Map<String, Object> raw = getBatchInternal(mapping);
        Map<String, T> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(key, targetType.cast(value)));
        return result;
    }
    
    /*
     * Cluster 版批次讀取：所有 key 同型別。
     * 例如：
     *   Map<String, User> users = OpenRedisString.getBatchCluster(List.of("user:1", "user:2"), User.class);
     */
    public static <T> Map<String, T> getBatchCluster(Collection<String> keys,
                                                     Class<T> targetType) {
        Objects.requireNonNull(targetType, "type is required");
        Map<String, Class<?>> mapping = new LinkedHashMap<>();
        if (keys != null) {
            keys.forEach(key -> mapping.put(Objects.requireNonNull(key, "key is required"), targetType));
        }
        Map<String, Object> raw = getBatchClusterInternal(mapping);
        Map<String, T> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(key, targetType.cast(value)));
        return result;
    }
    
    /*
     * 批次讀取：每個 key 對應不同型別。
     * 例如：
     *   Map<String, Object> data = OpenRedisString.getBatch(
     *       List.of(Map.of("user:1", User.class, "profile:1", Profile.class))
     *   );
     */
    public static Map<String, Object> getBatch(Collection<Map<String, Class<?>>> keyTypeMappings) {
        return getBatchInternal(flattenKeyTypeMappings(keyTypeMappings));
    }
    
    /*
     * Cluster 版批次讀取：每個 key 對應不同型別。
     * 例如：
     *   Map<String, Object> data = OpenRedisString.getBatchCluster(
     *       List.of(Map.of("user:1", User.class, "profile:1", Profile.class))
     *   );
     */
    public static Map<String, Object> getBatchCluster(Collection<Map<String, Class<?>>> keyTypeMappings) {
        return getBatchClusterInternal(flattenKeyTypeMappings(keyTypeMappings));
    }
    
    /*
     * fire-and-forget 模式，不關心結果。
     * 例如：
     *   OpenRedisString.setAsyncFireAndForget("cache:foo", payload, Duration.ofSeconds(30));
     */
    public static void setAsyncFireAndForget(String key,
                                             Object value,
                                             Duration ttl) {
        StringRedisTemplate stringRedis = stringRedisTemplate();
        Duration ttlWithJitter = withJitter(ttl);
        ForkJoinPool.commonPool().execute(() -> {
            try {
                stringRedis.opsForValue().set(key, OpenObjectMapper.toJson(value), ttlWithJitter);
            } catch (Exception ignored) {
                // 可選擇 log.warn("Redis async set failed", e)
            }
        });
    }
    
    private static Map<String, Object> getBatchInternal(Map<String, Class<?>> keyTypeMapping) {
        if (keyTypeMapping == null || keyTypeMapping.isEmpty()) {
            return Map.of();
        }
        RedisTemplate<String, Object> redis = redisTemplate();
        SessionCallback<Object> callback = new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                @SuppressWarnings("unchecked")
                RedisOperations<String, Object> ops = (RedisOperations<String, Object>) operations;
                keyTypeMapping.keySet().forEach(ops.opsForValue()::get);
                return null;
            }
        };
        Iterator<Object> values = redis.executePipelined(callback).iterator();
        Map<String, Object> result = new LinkedHashMap<>();
        keyTypeMapping.forEach((key, type) -> {
            Object value = values.hasNext() ? values.next() : null;
            result.put(key, OpenObjectMapper.convert(value, type));
        });
        return result;
    }
    
    private static Map<String, Object> getBatchClusterInternal(Map<String, Class<?>> keyTypeMapping) {
        if (keyTypeMapping == null || keyTypeMapping.isEmpty()) {
            return Map.of();
        }
        RedisTemplate<String, Object> redis = redisTemplate();
        Map<Integer, Collection<Map.Entry<String, Class<?>>>> grouped = new LinkedHashMap<>();
        keyTypeMapping.entrySet().forEach(entry -> grouped
                                                           .computeIfAbsent(RedisClusterUtil.slot(entry.getKey()), k -> new ArrayList<>())
                                                           .add(entry));
        Map<String, Object> result = new LinkedHashMap<>();
        grouped.values().forEach(group -> {
            SessionCallback<Object> callback = new SessionCallback<>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) {
                    @SuppressWarnings("unchecked")
                    RedisOperations<String, Object> ops = (RedisOperations<String, Object>) operations;
                    group.forEach(e -> ops.opsForValue().get(e.getKey()));
                    return null;
                }
            };
            Iterator<Object> values = redis.executePipelined(callback).iterator();
            group.forEach(e -> {
                Object value = values.hasNext() ? values.next() : null;
                result.put(e.getKey(), OpenObjectMapper.convert(value, e.getValue()));
            });
        });
        return result;
    }
    
    private static Map<String, Class<?>> flattenKeyTypeMappings(Collection<Map<String, Class<?>>> keyTypeMappings) {
        Map<String, Class<?>> flattened = new LinkedHashMap<>();
        if (keyTypeMappings != null) {
            keyTypeMappings.stream()
                           .filter(Objects::nonNull)
                           .forEach(map -> map.forEach((key, type) -> flattened.put(
                                   Objects.requireNonNull(key, "key is required"),
                                   Objects.requireNonNull(type, "type is required"))));
        }
        return flattened;
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
    
    public record KeyValueTtl(String key, Object value, Duration ttl) {
    }
}
