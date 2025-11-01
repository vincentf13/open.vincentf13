package open.vincentf13.sdk.infra.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.sdk.core.log.OpenLog;
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

    private static volatile KafkaTemplate<String, Object> kafkaTemplate;
    private static volatile ObjectMapper objectMapper;

    private OpenKafkaProducer() {
    }

    public static void initialize(KafkaTemplate<String, Object> template, ObjectMapper mapper) {
        kafkaTemplate = Objects.requireNonNull(template, "kafkaTemplate");
        objectMapper = Objects.requireNonNull(mapper, "objectMapper");
    }

    public static <T> CompletableFuture<SendResult<String, Object>> send(String topic,
                                                                         String key,
                                                                         T msg,
                                                                         Map<String, Object> headers
                                                                        ) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(msg, "msg");
        KafkaTemplate<String, Object> template = requireTemplate();
        try {
            ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, msg);
            addHeaders(record, headers);
            OpenLog.debug(log, "KafkaSend", () -> "送出 Kafka 訊息", "topic", topic, "key", key, "payloadType", msg.getClass().getName());
            CompletableFuture<SendResult<String, Object>> sendFuture = adaptFuture(
                    template.send(record)
                                                                                  );
            sendFuture.whenComplete((result, throwable) ->
                                            logResult(result, throwable, topic, key)
                                   );
            return sendFuture;
        } catch (Exception ex) {
            CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
            future.completeExceptionally(ex);
            OpenLog.error(log, "KafkaSendFailed", "Kafka 訊息送出失敗", ex, "topic", topic, "key", key);
            return future;
        }
    }

    public static <T> CompletableFuture<SendResult<String, Object>> send(String topic,
                                                                         T msg
                                                                        ) {
        return send(topic, null, msg, Map.of());
    }

    /**
     * 非同步地批次發送多個訊息。
     * <p>
     * 這個方法允許你傳入一個物件集合，並為每個物件動態地提取 Kafka 訊息的 key 和 headers。
     *
     * <h3>範例用法：</h3>
     * <pre>{@code
     * // 假設你有一個 Order 類
     * class Order {
     *     String orderId;
     *     String type;
     *     // ... getters and setters
     * }
     *
     * List<Order> orders = List.of(new Order("id1", "buy"), new Order("id2", "sell"));
     * String topic = "order-topic";
     *
     * // 批次發送訊息
     * // 1. 從 Order 物件中提取 orderId 作為 Kafka 的 key
     * // 2. 從 Order 物件中提取 type 作為 header，並放入一個 Map
     * CompletableFuture<List<SendResult<String, Object>>> future = OpenKafkaProducer.sendBatch(
     *     topic,
     *     orders,
     *     order -> ((Order) order).getOrderId(),
     *     order -> Map.of("orderType", ((Order) order).getType())
     * );
     *
     * future.whenComplete((results, ex) -> {
     *     if (ex == null) {
     *         System.out.println("成功發送 " + results.size() + " 則訊息");
     *     } else {
     *         System.err.println("發送失敗: " + ex.getMessage());
     *     }
     * });
     * }</pre>
     */
    public static <T> CompletableFuture<List<SendResult<String, Object>>> sendBatch(
            String topic,
            Collection<? extends T> msgs,
            Function<Object, String> keyExtraction,
            Function<Object, Map<String, Object>> headerExtraction
                                                                                   ) {
        Objects.requireNonNull(msgs, "msgs");
        List<CompletableFuture<SendResult<String, Object>>> futures = new ArrayList<>(msgs.size());
        for (T message : msgs) {
            String key = keyExtraction != null ? keyExtraction.apply(message) : null;
            Map<String, Object> headers = headerExtraction != null ? headerExtraction.apply(message) : Map.of();
            if (headers == null) {
                headers = Map.of();
            }
            futures.add(
                    send(topic, key, message, headers)
                       );
        }
        return allOf(futures);
    }

    private static KafkaTemplate<String, Object> requireTemplate() {
        KafkaTemplate<String, Object> template = kafkaTemplate;
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

    private static void addHeaders(ProducerRecord<String, Object> record, Map<String, Object> headers) {
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
                               : requireMapper().writeValueAsBytes(headerValue);
                record.headers().add(headerKey, bytes);
            } catch (Exception ex) {
                OpenLog.warn(log, "KafkaHeaderEncodeFailed", "Kafka header 序列化失敗", ex, "headerKey", headerKey);
            }
        });
    }

    private static void logResult(
            SendResult<String, Object> result,
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

    private static <X> CompletableFuture<List<X>> allOf(List<CompletableFuture<X>> futures) {
        CompletableFuture<?>[] array = futures.toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(array)
                .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }
}