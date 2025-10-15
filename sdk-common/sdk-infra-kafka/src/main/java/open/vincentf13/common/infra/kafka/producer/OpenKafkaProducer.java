package open.vincentf13.common.infra.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.common.core.log.OpenLog;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Kafka Producer 靜態工具：將 payload 序列化為 bytes，並提供批次送出與自訂 key/header 的彈性。
 */
public final class OpenKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(OpenKafkaProducer.class);

    private static volatile KafkaTemplate<String, byte[]> kafkaTemplate;
    private static volatile ObjectMapper objectMapper;

    private OpenKafkaProducer() {
    }

    public static void initialize(KafkaTemplate<String, byte[]> template, ObjectMapper mapper) {
        kafkaTemplate = Objects.requireNonNull(template, "kafkaTemplate");
        objectMapper = Objects.requireNonNull(mapper, "objectMapper");
    }

    public static <T> CompletableFuture<SendResult<String, byte[]>> send(
            String topic,
            String key,
            T msg,
            Map<String, Object> headers
    ) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(msg, "msg");
        KafkaTemplate<String, byte[]> template = requireTemplate();
        ObjectMapper mapper = requireMapper();
        try {
            byte[] value = mapper.writeValueAsBytes(msg);
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, value);
            addHeaders(mapper, record, headers);
            OpenLog.debug(log, "KafkaSend", () -> "送出 Kafka 訊息", "topic", topic, "key", key, "payloadType", msg.getClass().getName());
            CompletableFuture<SendResult<String, byte[]>> sendFuture = adaptFuture(template.send(record));
            sendFuture.whenComplete((result, throwable) -> handleSendResult(result, throwable, topic, key));
            return sendFuture;
        } catch (Exception ex) {
            CompletableFuture<SendResult<String, byte[]>> future = new CompletableFuture<>();
            future.completeExceptionally(ex);
            OpenLog.error(log, "KafkaSendFailed", "Kafka 訊息送出失敗（序列化或建構）", ex, "topic", topic, "key", key);
            return future;
        }
    }

    public static <T> CompletableFuture<SendResult<String, byte[]>> send(
            String topic,
            T msg
    ) {
        return send(topic, null, msg, Map.of());
    }

    public static <T> CompletableFuture<List<SendResult<String, byte[]>>> sendBatch(
            String topic,
            Collection<? extends T> msgs,
            Function<Object, String> keyResolver,
            Function<Object, Map<String, Object>> headerResolver
    ) {
        Objects.requireNonNull(msgs, "msgs");
        List<CompletableFuture<SendResult<String, byte[]>>> futures = new ArrayList<>(msgs.size());
        for (T message : msgs) {
            String key = keyResolver != null ? keyResolver.apply(message) : null;
            Map<String, Object> headers = headerResolver != null ? headerResolver.apply(message) : Map.of();
            if (headers == null) {
                headers = Map.of();
            }
            futures.add(send(topic, key, message, headers));
        }
        return sequence(futures);
    }

    private static KafkaTemplate<String, byte[]> requireTemplate() {
        KafkaTemplate<String, byte[]> template = kafkaTemplate;
        if (template == null) {
            throw new IllegalStateException("OpenKafkaProducer 尚未初始化 kafkaTemplate");
        }
        return template;
    }

    private static ObjectMapper requireMapper() {
        ObjectMapper mapper = objectMapper;
        if (mapper == null) {
            throw new IllegalStateException("OpenKafkaProducer 尚未初始化 objectMapper");
        }
        return mapper;
    }

    private static void addHeaders(ObjectMapper mapper, ProducerRecord<String, byte[]> record, Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach((headerKey, headerValue) -> {
            if (headerValue == null) {
                return;
            }
            try {
                byte[] bytes = headerValue instanceof String value
                               ? value.getBytes(StandardCharsets.UTF_8)
                               : mapper.writeValueAsBytes(headerValue);
                record.headers().add(headerKey, bytes);
            } catch (Exception ex) {
                OpenLog.warn(log, "KafkaHeaderEncodeFailed", "Kafka header 序列化失敗", ex, "headerKey", headerKey);
            }
        });
    }

    private static void handleSendResult(
            SendResult<String, byte[]> result,
            Throwable throwable,
            String topic,
            String key
    ) {
        if (throwable != null) {
            OpenLog.error(log, "KafkaSendError", "Kafka 訊息送出失敗", throwable, "topic", topic, "key", key);
            return;
        }
        RecordMetadata metadata = result != null ? result.getRecordMetadata() : null;
        if (metadata != null) {
            OpenLog.debug(log, "KafkaSendSuccess", () -> "Kafka 訊息送出成功",
                    "topic", metadata.topic(),
                    "partition", metadata.partition(),
                    "offset", metadata.offset(),
                    "timestamp", metadata.timestamp(),
                    "key", key);
        } else {
            OpenLog.debug(log, "KafkaSendSuccess", () -> "Kafka 訊息送出成功", "topic", topic, "key", key);
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> CompletableFuture<SendResult<K, V>> adaptFuture(Object future) {
        if (future instanceof CompletableFuture<?> completableFuture) {
            return (CompletableFuture<SendResult<K, V>>) completableFuture;
        }
        if (future instanceof ListenableFuture<?> listenableFuture) {
            return toCompletableFuture((ListenableFuture<SendResult<K, V>>) listenableFuture);
        }
        throw new IllegalStateException("Unsupported KafkaTemplate future type: " + future.getClass().getName());
    }

    private static <K, V> CompletableFuture<SendResult<K, V>> toCompletableFuture(
            ListenableFuture<SendResult<K, V>> listenableFuture
    ) {
        CompletableFuture<SendResult<K, V>> completableFuture = new CompletableFuture<>();
        listenableFuture.addCallback(completableFuture::complete, completableFuture::completeExceptionally);
        return completableFuture;
    }

    private static <X> CompletableFuture<List<X>> sequence(List<CompletableFuture<X>> futures) {
        CompletableFuture<?>[] array = futures.toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(array)
                .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }
}
