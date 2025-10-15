package test.open.vincentf13.common.core.test;

import open.vincentf13.common.core.test.KafkaTestSupport;
import open.vincentf13.common.core.test.OpenKafkaTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest(
        classes = KafkaTest.KafkaTestConfig.class,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "spring.main.lazy-initialization=true",
                "spring.jmx.enabled=false"
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class KafkaTest {

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        OpenKafkaTestContainer.register(registry);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @BeforeEach
    void prepareTopic() {
        KafkaTestSupport.prepareTopic(kafkaAdmin);
    }

    @AfterEach
    void clearTopic() {
        KafkaTestSupport.clearTopic();
    }

    @Test
    void sendAndReceive() throws Exception {
        AtomicReference<String> payload = new AtomicReference<>();
        CountDownLatch latch = KafkaTestSupport.expectPayload(payload);

        KafkaMessageListenerContainer<String, String> listenerContainer = KafkaTestSupport.startListener(
                consumerFactory,
                KafkaTestSupport.currentTopic(),
                KafkaTestSupport.buildPayloadListener(payload, latch));
        try {
            kafkaTemplate.send(KafkaTestSupport.currentTopic(), "k1", "v1").get();
            KafkaTestSupport.assertReceived(latch, payload, "v1");
        } finally {
            KafkaTestSupport.stopListener(listenerContainer);
        }
    }

    @TestConfiguration
    @EnableAutoConfiguration
    static class KafkaTestConfig {
    }
}
