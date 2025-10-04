package open.vincentf13.common.core.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Import(RedisIntegrationTests.RedisTestConfiguration.class)
class RedisIntegrationTests extends AbstractIntegrationTest {

  private static final String KEY = "sdk-core-test:demo";

  @Autowired
  private StringRedisTemplate redisTemplate;

  @Test
  void writeAndReadValue() {
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
        connection.ping();
      }
    });

    redisTemplate.delete(KEY);
    redisTemplate.opsForValue().set(KEY, "cached-value");
    assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo("cached-value");
  }

  @TestConfiguration
  static class RedisTestConfiguration {

    @Bean
    LettuceConnectionFactory lettuceConnectionFactory() {
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
