package open.vincentf13.common.infra.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.common.core.log.FastLog;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 預設 Kafka Producer 實作：payload 轉為 bytes，並可透過客製 key/header 解析器擴充行為。
 */
public class KafkaProducerServiceImpl<T> implements KafkaProducerService<T> {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerServiceImpl.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Function<Object, String> defaultKey;
    private final Function<Object, Map<String, Object>> defaultHeaders;

    public KafkaProducerServiceImpl(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            ObjectMapper objectMapper,
            Function<Object, String> defaultKey,
            Function<Object, Map<String, Object>> defaultHeaders
                                   ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.defaultKey = defaultKey;
        this.defaultHeaders = defaultHeaders;
    }

    @Override
    public CompletableFuture<SendResult<String, byte[]>> send(
            String topic,
            String key,
            T msg,
            Map<String, Object> headers
                                                             ) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(msg, "msg");
        try {
            byte[] value = objectMapper.writeValueAsBytes(msg);
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, value);
            addHeaders(record, headers);
            FastLog.debug(log, "KafkaSend", () -> "送出 Kafka 訊息", "topic", topic, "key", key, "payloadType", msg.getClass().getName());
            CompletableFuture<SendResult<String, byte[]>> sendFuture = adaptFuture(kafkaTemplate.send(record));
            sendFuture.whenComplete((result, throwable) -> handleSendResult(result, throwable, topic, key));
            return sendFuture;
        } catch (Exception ex) {
            CompletableFuture<SendResult<String, byte[]>> future = new CompletableFuture<>();
            future.completeExceptionally(ex);
            FastLog.error(log, "KafkaSendFailed", "Kafka 訊息送出失敗（序列化或建構）", ex, "topic", topic, "key", key);
            return future;
        }
    }

    @Override
    public CompletableFuture<SendResult<String, byte[]>> send(String topic, T msg) {
        String key = defaultKey != null ? defaultKey.apply(msg) : null;
        Map<String, Object> headers = defaultHeaders != null ? defaultHeaders.apply(msg) : Map.of();
        return send(topic, key, msg, headers);
    }

    @Override
    public CompletableFuture<List<SendResult<String, byte[]>>> sendBatch(
            String topic,
            Collection<? extends T> msgs
                                                                        ) {
        Objects.requireNonNull(msgs, "msgs");
        List<CompletableFuture<SendResult<String, byte[]>>> futures = new ArrayList<>(msgs.size());
        for (T message : msgs) {
            futures.add(send(topic, message));
        }
        return sequence(futures);
    }

    private void addHeaders(ProducerRecord<String, byte[]> record, Map<String, Object> headers) {
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
                               : objectMapper.writeValueAsBytes(headerValue);
                record.headers().add(headerKey, bytes);
            } catch (Exception ex) {
                FastLog.warn(log, "KafkaHeaderEncodeFailed", "Kafka header 序列化失敗", ex, "headerKey", headerKey);
            }
        });
    }

    private void handleSendResult(
            SendResult<String, byte[]> result,
            Throwable throwable,
            String topic,
            String key
    ) {
        if (throwable != null) {
            FastLog.error(log, "KafkaSendError", "Kafka 訊息送出失敗", throwable, "topic", topic, "key", key);
            return;
        }
        RecordMetadata metadata = result != null ? result.getRecordMetadata() : null;
        if (metadata != null) {
            FastLog.debug(log, "KafkaSendSuccess", () -> "Kafka 訊息送出成功",
                    "topic", metadata.topic(),
                    "partition", metadata.partition(),
                    "offset", metadata.offset(),
                    "timestamp", metadata.timestamp(),
                    "key", key);
        } else {
            FastLog.debug(log, "KafkaSendSuccess", () -> "Kafka 訊息送出成功", "topic", topic, "key", key);
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
