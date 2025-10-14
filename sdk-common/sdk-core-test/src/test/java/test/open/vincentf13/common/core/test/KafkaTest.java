package test.open.vincentf13.common.core.test;

import open.vincentf13.common.core.test.OpenKafkaTestContainer;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = KafkaTest.KafkaTestConfig.class,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "spring.main.lazy-initialization=true",
                "spring.jmx.enabled=false"
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
public class KafkaTest {

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        OpenKafkaTestContainer.register(registry);
    }
    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    TestListener listener;

    @Test
    void send_and_receive() throws Exception {
        kafkaTemplate.send(OpenKafkaTestContainer.topic(), "k1", "v1").get(5, TimeUnit.SECONDS);
        assertTrue(listener.latch.await(5, TimeUnit.SECONDS));
        assertEquals("v1", listener.payload);
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
}
