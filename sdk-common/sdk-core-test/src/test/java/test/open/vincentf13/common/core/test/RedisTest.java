package test.open.vincentf13.common.core.test;

import open.vincentf13.common.core.test.OpenRedisTestContainer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

// Redis 容器整合測試：透過動態連線工廠驗證暫存操作
@DataRedisTest
class RedisTest {

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        OpenRedisTestContainer.register(registry);
    }

    private static final String KEY = "sdk-core-test:demo";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void writeAndReadValue() {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
                // 等待容器真正就緒，避免瞬時連線錯誤
                connection.ping();
            }
        });
        redisTemplate.delete(KEY);
        redisTemplate.opsForValue().set(KEY, "cached-value");
        Assertions.assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo("cached-value");
    }

}
