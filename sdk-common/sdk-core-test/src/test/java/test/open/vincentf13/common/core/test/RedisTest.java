package test.open.vincentf13.common.core.test;

import open.vincentf13.common.core.test.OpenRedisTestContainer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import open.vincentf13.common.core.test.RedisTestSupport;

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
        RedisTestSupport.awaitRedisReady(redisTemplate);

        redisTemplate.delete(KEY);
        redisTemplate.opsForValue().set(KEY, "cached-value");
        Assertions.assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo("cached-value");
    }

}
