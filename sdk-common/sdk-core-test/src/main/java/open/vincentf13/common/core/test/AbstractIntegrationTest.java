package open.vincentf13.common.core.test;

import java.util.stream.Stream;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Base class for container-backed integration tests. Each extending test automatically boots a
 * Spring context that connects to ephemeral MySQL, Kafka, and Redis containers started by
 * Testcontainers.
 */
@Testcontainers
@SpringBootTest(classes = AbstractIntegrationTest.TestApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest {

  @Container
  protected static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
          .withUsername("test")
          .withPassword("test")
          .withDatabaseName("app");

  @Container
  protected static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

  @Container
  protected static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.2"))
          .withExposedPorts(6379)
          .waitingFor(Wait.forListeningPort());

  @DynamicPropertySource
  static void registerProps(DynamicPropertyRegistry registry) {
    ensureContainersStarted();
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  private static void ensureContainersStarted() {
    Startables.deepStart(Stream.of(MYSQL, KAFKA, REDIS).filter(container -> !container.isRunning()))
        .join();
  }

  @SpringBootApplication(scanBasePackages = "open.vincentf13")
  static class TestApplication {}
}
