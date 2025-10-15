package test.open.vincentf13.common.core.test;

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


/**
 * Kafka 容器整合測試
 * 使用 dokcer Kafka 容器運行 Kafka 測試
 * 各測試方法前後 動態建立 隨機topic，各測試互相隔離，可使用平行測試，提升效能。
 * 若配置 open.vincentf13.common.core.test.testcontainer.kafka.enabled=false
 * 則連到真實數據庫，不啟用 kafka 容器
 */
@SpringBootTest(
        classes = KafkaTest.KafkaTestConfig.class,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
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
        // 為每個測試建立隔離的 topic，避免平行測試互相干擾
        OpenKafkaTestContainer.prepareTopic(kafkaAdmin);
    }

    @AfterEach
    void clearTopic() {
        OpenKafkaTestContainer.clearTopic();
    }

    @Test
    void sendAndReceive() throws Exception {
        AtomicReference<String> payload = new AtomicReference<>(null);
        CountDownLatch latch = new CountDownLatch(1);

        KafkaMessageListenerContainer<String, String> container = OpenKafkaTestContainer.startListener(
                consumerFactory,
                OpenKafkaTestContainer.currentTopic(),
                OpenKafkaTestContainer.buildListener(payload, latch));
        try {
            String data = "v1";
            // 送出訊息並等待 listener 收到後將 latch 倒扣
            kafkaTemplate.send(OpenKafkaTestContainer.currentTopic(), "k1", data).get();
            OpenKafkaTestContainer.assertReceived(latch, payload, data);
        } finally {
            // 測試完成後確保 listener 停止，不留背景執行緒
            OpenKafkaTestContainer.stopListener(container);
        }
    }

    @TestConfiguration
    @EnableAutoConfiguration
    static class KafkaTestConfig {
    }
}
