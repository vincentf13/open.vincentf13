package open.vincentf13.common.core.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

/**
 * 靜態 Kafka Testcontainer 工具，集中管理容器啟動與屬性註冊。
 */
public final class OpenKafkaTestContainer {

    private static final ToggleableKafkaContainer KAFKA =
            new ToggleableKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
    private static final String TOPIC = "t-" + UUID.randomUUID();

    private OpenKafkaTestContainer() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        if (!TestContainerSettings.kafkaEnabled()) {
            return;
        }
        KAFKA.start();
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.topic", OpenKafkaTestContainer::topic);
    }

    public static String topic() {
        return TOPIC;
    }

    public static KafkaContainer container() {
        return KAFKA;
    }

    private static final class ToggleableKafkaContainer extends KafkaContainer {
        private ToggleableKafkaContainer(DockerImageName dockerImageName) {
            super(dockerImageName);
            withStartupTimeout(Duration.ofMinutes(2));
        }

        @Override
        public void start() {
            if (!TestContainerSettings.kafkaEnabled() || isRunning()) {
                return;
            }
            super.start();
        }

        @Override
        public void stop() {
            if (!TestContainerSettings.kafkaEnabled()) {
                return;
            }
            super.stop();
        }
    }
}
