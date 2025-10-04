package test.open.vincentf13.common.core.test;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(
        classes = {TestBoot.class, KafkaTest.KafkaTestConfig.class},
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
)
public class KafkaTest {

    @Container
    protected static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")); // 單 Broker 測試用

    static final String TOPIC = "t-" + UUID.randomUUID();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        r.add("app.topic", () -> TOPIC);
    }

    @TestConfiguration
    @EnableAutoConfiguration   // 讓 Spring Kafka 自動建立 KafkaTemplate/Factories
    static class KafkaTestConfig {
        @Bean
        NewTopic topic(@Value("${app.topic}") String name) {
            return TopicBuilder.name(name).partitions(1).replicas(1).build();
        }

        @Bean
        TestListener testListener() {
            return new TestListener();
        }
    }

    static class TestListener {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile String payload;

        @KafkaListener(topics = "${app.topic}")
        void onMessage(String v) {
            payload = v;
            latch.countDown();
        }
    }

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    TestListener listener;

    @Test
    void send_and_receive() throws Exception {
        kafkaTemplate.send(TOPIC, "k1", "v1").get(5, TimeUnit.SECONDS);
        assertTrue(listener.latch.await(5, TimeUnit.SECONDS));
        assertEquals("v1", listener.payload);
    }
}
