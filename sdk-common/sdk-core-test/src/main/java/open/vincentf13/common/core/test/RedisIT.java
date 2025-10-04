package open.vincentf13.common.core.test;

import open.vincentf13.common.core.test.contract.AbstractIT;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

// Redis 容器整合測試：透過動態連線工廠驗證暫存操作
@DataRedisTest
@Import(RedisIT.RedisTestConfiguration.class)
class RedisIT extends AbstractIT {

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

  @TestConfiguration
  static class RedisTestConfiguration {

    @Bean
    LettuceConnectionFactory lettuceConnectionFactory() {
      // 以容器暴露的 host/port 建立專屬測試連線工廠
      RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
      configuration.setHostName(REDIS.getHost());
      configuration.setPort(REDIS.getMappedPort(6379));
      LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
      factory.afterPropertiesSet();
      return factory;
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
      return new StringRedisTemplate(connectionFactory);
    }
  }
}
