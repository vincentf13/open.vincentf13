package open.vincentf13.common.core.test;

import open.vincentf13.common.core.test.contract.BaseIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Kafka 容器整合測試：驗證 KafkaTemplate 能與臨時 Broker 完整收發
@SpringBootTest(classes = KafkaIT.TestConfig.class)
class KafkaIT extends BaseIT {

  private static final String TOPIC = "sdk-core-test.demo";

  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;

  @Autowired
  private InMemoryKafkaListener listener;

  @BeforeEach
  void cleanTopicBuffer() {
    // 測試前清除暫存訊息避免案例互相干擾
    listener.clear();
  }

  @Test
  void kafkaRoundTrip() {
    kafkaTemplate.send(TOPIC, "k1", "{\"payload\":\"v1\"}");

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
        assertThat(listener.values(TOPIC)).contains("{\"payload\":\"v1\"}"));
  }

  @TestConfiguration
  @EnableKafka
  static class KafkaTestConfiguration {

    @Bean
    InMemoryKafkaListener inMemoryKafkaListener() {
      // 以簡易記憶體 listener 蒐集測試中的消費結果
      return new InMemoryKafkaListener();
    }

    @Bean
    org.apache.kafka.clients.admin.NewTopic demoTopic() {
      return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
    }
  }

  static class InMemoryKafkaListener {
    private final Map<String, List<String>> messages = new ConcurrentHashMap<>();

    @KafkaListener(topics = TOPIC)
    void consume(@Header(KafkaHeaders.RECEIVED_TOPIC) String topic, String payload) {
      // 直接緩存訊息內容讓測試可斷言 round-trip 結果
      messages.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(payload);
    }

    void clear() {
      messages.clear();
    }

    List<String> values(String topic) {
      return messages.getOrDefault(topic, List.of());
    }
  }

  @EnableAutoConfiguration
  @Import(KafkaTestConfiguration.class)
  static class TestConfig {
  }
}
