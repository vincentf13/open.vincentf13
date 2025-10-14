package open.vincentf13.common.core.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * 靜態 Redis Testcontainer 工具，集中管理容器啟動與屬性註冊。
 */
public final class OpenRedisTestContainer {

    private static final ToggleableRedisContainer REDIS =
            new ToggleableRedisContainer()
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort());

    private OpenRedisTestContainer() {
    }

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

    private static final class ToggleableRedisContainer extends GenericContainer<ToggleableRedisContainer> {

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
