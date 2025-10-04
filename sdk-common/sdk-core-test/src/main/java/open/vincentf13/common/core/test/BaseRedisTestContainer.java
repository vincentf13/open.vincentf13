package open.vincentf13.common.core.test;

import org.junit.jupiter.api.TestInstance;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 容器化整合測試的共用基底：啟動並共用 Testcontainers 的 MySQL/Kafka/Redis 與對應連線屬性。
 */
@Testcontainers                                                     // 讓 JUnit 5 管理容器生命週期
@TestInstance(TestInstance.Lifecycle.PER_METHOD)                     // 每個測試方法建立新測試實例（避免共享狀態）
public abstract class BaseRedisTestContainer {

    // 注意：static final 讓「此基底類」與其所有子類共用同一組容器（同一 JVM 內）
    @Container
    public static final ToggleableRedisContainer REDIS =
            new ToggleableRedisContainer()
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort());

    // 將容器連線資訊註冊到 Spring Environment，讓 DataSource/Kafka/Redis 指向這些臨時容器
    @DynamicPropertySource
    public static void registerProps(DynamicPropertyRegistry registry) {
        if (!TestContainerSettings.redisEnabled()) {
            return;
        }

        REDIS.start();
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static class ToggleableRedisContainer extends GenericContainer<ToggleableRedisContainer> {

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
