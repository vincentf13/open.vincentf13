package open.vincentf13.common.core.test;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kafka 測試工具：提供 per-thread topic 與基本的 Container 等待工具。
 */
public final class KafkaTestSupport {

    private KafkaTestSupport() {
    }

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final ThreadLocal<String> CURRENT_TOPIC = new ThreadLocal<>();

    public static String prepareTopic(KafkaAdmin kafkaAdmin) {
        String topic = "kafka-test-" + UUID.randomUUID();
        kafkaAdmin.createOrModifyTopics(new NewTopic(topic, 1, (short) 1));
        CURRENT_TOPIC.set(topic);
        return topic;
    }

    public static String currentTopic() {
        return CURRENT_TOPIC.get();
    }

    public static void clearTopic() {
        CURRENT_TOPIC.remove();
    }

    public static <K, V> KafkaMessageListenerContainer<K, V> startListener(
            ConsumerFactory<K, V> consumerFactory,
            String topic,
            MessageListener<K, V> listener) throws InterruptedException {
        ContainerProperties properties = new ContainerProperties(topic);
        properties.setGroupId("test-group-" + UUID.randomUUID());
        properties.setMessageListener(listener);

        KafkaMessageListenerContainer<K, V> container =
                new KafkaMessageListenerContainer<>(consumerFactory, properties);
        container.start();
        awaitAssignment(container, DEFAULT_TIMEOUT);
        return container;
    }

    public static void stopListener(KafkaMessageListenerContainer<?, ?> container) throws InterruptedException {
        try {
            container.stop();
        } finally {
            awaitStop(container, DEFAULT_TIMEOUT);
        }
    }

    public static <V> CountDownLatch expectPayload(AtomicReference<V> holder) {
        CountDownLatch latch = new CountDownLatch(1);
        holder.set(null);
        return latch;
    }

    public static <V> MessageListener<String, V> buildPayloadListener(AtomicReference<V> holder, CountDownLatch latch) {
        return record -> {
            holder.set(record.value());
            latch.countDown();
        };
    }

    public static <V> void assertReceived(CountDownLatch latch, AtomicReference<V> holder, V expected)
            throws InterruptedException {
        assertTrue(latch.await(DEFAULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        assertEquals(expected, holder.get());
    }

    public static void awaitAssignment(KafkaMessageListenerContainer<?, ?> container, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (container.getAssignedPartitions().isEmpty()) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Kafka listener container was not assigned within timeout");
            }
            Thread.sleep(50);
        }
    }

    public static void awaitStop(KafkaMessageListenerContainer<?, ?> container, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (container.isRunning()) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Kafka listener container did not stop within timeout");
            }
            Thread.sleep(50);
        }
    }
}
