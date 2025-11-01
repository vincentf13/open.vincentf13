package test.open.vincentf13.sdk.core.test;

import open.vincentf13.sdk.core.test.OpenRedisTestContainer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;



/**
 *  Redis 容器整合測試
 * <p>
 * 使用 dokcer redis容器運行 redis 測試
 * 各測試方法前 生成隨機key，各測試互相隔離，可使用平行測試，提升效能。
 * <p>
 * 若配置 open.vincentf13.sdk.core.test.testcontainer.redis.enabled=false
 * 則連到真實數據庫，不啟用 redis 容器
 * 真實數據庫配置：spring.data.redis.host/port/password
 */
@DataRedisTest
class RedisTest {

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        OpenRedisTestContainer.register(registry);
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String key;

    @BeforeEach
    void generateKey() {
        key = "test:" + UUID.randomUUID();
    }

    @Test
    void writeAndReadValue() {
        OpenRedisTestContainer.awaitReady(redisTemplate);

        redisTemplate.delete(key);
        redisTemplate.opsForValue().set(key, "cached-value");
        Assertions.assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("cached-value");
    }
}
