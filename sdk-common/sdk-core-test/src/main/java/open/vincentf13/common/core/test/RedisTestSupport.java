package open.vincentf13.common.core.test;

import org.awaitility.Awaitility;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;

/**
 * 測試支援工具：封裝等待 Redis 容器就緒的流程。
 */
public final class RedisTestSupport {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private RedisTestSupport() {
    }

    public static void awaitRedisReady(StringRedisTemplate redisTemplate) {
        awaitRedisReady(redisTemplate, DEFAULT_TIMEOUT);
    }

    public static void awaitRedisReady(StringRedisTemplate redisTemplate, Duration timeout) {
        Objects.requireNonNull(redisTemplate, "redisTemplate");
        Awaitility.await().atMost(timeout).untilAsserted(() -> {
            RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
            Objects.requireNonNull(factory, "RedisConnectionFactory");
            try (RedisConnection connection = factory.getConnection()) {
                connection.ping();
            }
        });
    }
}
