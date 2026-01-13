package open.vincentf13.sdk.core.test;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** 靜態 Kafka Testcontainer 工具，集中管理容器啟動與屬性註冊。 */
public final class OpenKafkaTestContainer {

  private static final ToggleableKafkaContainer KAFKA =
      new ToggleableKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
  private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);
  private static final ThreadLocal<String> CURRENT_TOPIC = new ThreadLocal<>();
  private static volatile KafkaAdmin kafkaAdmin;
  private static volatile ConsumerFactory<?, ?> consumerFactory;

  private OpenKafkaTestContainer() {}

  public static void register(DynamicPropertyRegistry registry) {
    if (!TestContainerSettings.kafkaEnabled()) {
      return;
    }
    KAFKA.start();
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  public static String newTopicName() {
    return "kafka-test-" + UUID.randomUUID();
  }

  /** 建立唯一 topic 並記錄於 ThreadLocal，讓同一測試執行緒能安全取得對應 topic。 */
  public static String prepareTopic() {
    KafkaAdmin admin = requireKafkaAdmin();
    String topic = newTopicName();
    admin.createOrModifyTopics(new NewTopic(topic, 1, (short) 1));
    CURRENT_TOPIC.set(topic);
    return topic;
  }

  public static String currentTopic() {
    return CURRENT_TOPIC.get();
  }

  public static void clearTopic() {
    CURRENT_TOPIC.remove();
  }

  /** 啟動簡單的 listener container，並等待分區就緒後再回傳。 */
  public static <K, V> KafkaMessageListenerContainer<K, V> startListener(
      String topic, MessageListener<K, V> listener) throws InterruptedException {
    ContainerProperties properties = new ContainerProperties(topic);
    properties.setGroupId("test-group-" + UUID.randomUUID());
    properties.setMessageListener(listener);

    KafkaMessageListenerContainer<K, V> container =
        new KafkaMessageListenerContainer<>(resolveConsumerFactory(), properties);
    container.start();
    awaitAssignment(container, WAIT_TIMEOUT);
    return container;
  }

  /** 停用 listener，並等待執行緒完全結束以免污染後續測試。 */
  public static void stopListener(KafkaMessageListenerContainer<?, ?> container)
      throws InterruptedException {
    try {
      container.stop();
    } finally {
      awaitStop(container, WAIT_TIMEOUT);
    }
  }

  public static void configure(KafkaAdmin kafkaAdmin, ConsumerFactory<?, ?> consumerFactory) {
    OpenKafkaTestContainer.kafkaAdmin = Objects.requireNonNull(kafkaAdmin, "kafkaAdmin");
    OpenKafkaTestContainer.consumerFactory =
        Objects.requireNonNull(consumerFactory, "consumerFactory");
  }

  public static <V> MessageListener<String, V> buildListener(
      AtomicReference<V> holder, CountDownLatch latch) {
    return record -> {
      holder.set(record.value());
      latch.countDown();
    };
  }

  public static <V> void assertReceived(CountDownLatch latch, AtomicReference<V> holder, V expected)
      throws InterruptedException {
    if (!latch.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
      throw new IllegalStateException("Kafka payload not received within " + WAIT_TIMEOUT);
    }
    V actual = holder.get();
    if (!Objects.equals(expected, actual)) {
      throw new IllegalStateException(
          "Kafka payload mismatch. expected=" + expected + ", actual=" + actual);
    }
  }

  private static void awaitAssignment(
      KafkaMessageListenerContainer<?, ?> container, Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (container.getAssignedPartitions().isEmpty()) {
      if (System.nanoTime() > deadline) {
        throw new IllegalStateException("Kafka listener container was not assigned within timeout");
      }
      Thread.sleep(50);
    }
  }

  private static void awaitStop(KafkaMessageListenerContainer<?, ?> container, Duration timeout)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (container.isRunning()) {
      if (System.nanoTime() > deadline) {
        throw new IllegalStateException("Kafka listener container did not stop within timeout");
      }
      Thread.sleep(50);
    }
  }

  private static KafkaAdmin requireKafkaAdmin() {
    KafkaAdmin admin = kafkaAdmin;
    if (admin == null) {
      throw new IllegalStateException(
          "KafkaAdmin has not been configured for OpenKafkaTestContainer");
    }
    return admin;
  }

  @SuppressWarnings("unchecked")
  private static <K, V> ConsumerFactory<K, V> resolveConsumerFactory() {
    ConsumerFactory<?, ?> factory = consumerFactory;
    if (factory == null) {
      throw new IllegalStateException(
          "ConsumerFactory has not been configured for OpenKafkaTestContainer");
    }
    return (ConsumerFactory<K, V>) factory;
  }

  @Configuration(proxyBeanMethods = false)
  public static class DependencyInitializer {

    private final ObjectProvider<KafkaAdmin> kafkaAdmin;
    private final ObjectProvider<ConsumerFactory<?, ?>> consumerFactory;

    public DependencyInitializer(
        ObjectProvider<KafkaAdmin> kafkaAdmin,
        ObjectProvider<ConsumerFactory<?, ?>> consumerFactory) {
      this.kafkaAdmin = kafkaAdmin;
      this.consumerFactory = consumerFactory;
    }

    @PostConstruct
    void initializeDependencies() {
      KafkaAdmin admin = kafkaAdmin.getIfAvailable();
      ConsumerFactory<?, ?> factory = consumerFactory.getIfAvailable();
      if (admin != null && factory != null) {
        OpenKafkaTestContainer.configure(admin, factory);
      }
    }
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
