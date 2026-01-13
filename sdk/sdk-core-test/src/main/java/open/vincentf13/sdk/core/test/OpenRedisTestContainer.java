package open.vincentf13.sdk.core.test;

import java.time.Duration;
import java.util.Objects;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** 靜態 Redis Testcontainer 工具，集中管理容器啟動與屬性註冊。 */
public final class OpenRedisTestContainer {

  private static final ToggleableRedisContainer REDIS =
      new ToggleableRedisContainer().withExposedPorts(6379).waitingFor(Wait.forListeningPort());

  private static final Duration DEFAULT_READY_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration RETRY_INTERVAL = Duration.ofMillis(200);

  private OpenRedisTestContainer() {}

  public static void register(DynamicPropertyRegistry registry) {
    if (!TestContainerSettings.redisEnabled()) {
      return;
    }
    REDIS.start();
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  public static GenericContainer<?> container() {
    return REDIS;
  }

  /** 等待 redis 連線工廠可用，確保容器或外部 Redis 已經就緒。 */
  public static void awaitReady(StringRedisTemplate redisTemplate) {
    awaitReady(redisTemplate, DEFAULT_READY_TIMEOUT);
  }

  public static void awaitReady(StringRedisTemplate redisTemplate, Duration timeout) {
    Objects.requireNonNull(redisTemplate, "redisTemplate");
    Objects.requireNonNull(timeout, "timeout");

    RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
    Objects.requireNonNull(factory, "RedisConnectionFactory");

    long deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      try (RedisConnection connection = factory.getConnection()) {
        connection.ping();
        return;
      } catch (RuntimeException ex) {
        if (System.nanoTime() > deadline) {
          throw new IllegalStateException(
              "Redis is not ready within " + timeout.toMillis() + "ms", ex);
        }
        sleepQuietly(RETRY_INTERVAL);
      }
    }
  }

  private static void sleepQuietly(Duration duration) {
    try {
      Thread.sleep(Math.max(50, duration.toMillis()));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for Redis readiness", ex);
    }
  }

  private static final class ToggleableRedisContainer
      extends GenericContainer<ToggleableRedisContainer> {

    private ToggleableRedisContainer() {
      super(DockerImageName.parse("redis:7.2"));
    }

    @Override
    public void start() {
      if (!TestContainerSettings.redisEnabled() || isRunning()) {
        return;
      }
      super.start();
    }

    @Override
    public void stop() {
      if (!TestContainerSettings.redisEnabled()) {
        return;
      }
      super.stop();
    }
  }
}
