package open.vincentf13.common.infra.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;

public class KafkaProducerServiceImpl<T> implements KafkaProducerService<T> {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerServiceImpl.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Function<Object, String> keyFunction;
    private final Function<Object, Map<String, Object>> headerFunction;

    public KafkaProducerServiceImpl(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            ObjectMapper objectMapper,
            Function<Object, String> keyFunction,
            Function<Object, Map<String, Object>> headerFunction
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.keyFunction = keyFunction;
        this.headerFunction = headerFunction;
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
            return adaptFuture(kafkaTemplate.send(record));
        } catch (Exception ex) {
            CompletableFuture<SendResult<String, byte[]>> future = new CompletableFuture<>();
            future.completeExceptionally(ex);
            return future;
        }
    }

    @Override
    public CompletableFuture<SendResult<String, byte[]>> send(String topic, T msg) {
        String key = keyFunction != null ? keyFunction.apply(msg) : null;
        Map<String, Object> headers = headerFunction != null ? headerFunction.apply(msg) : Map.of();
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
                log.warn("Failed to encode Kafka header, key={}", headerKey, ex);
            }
        });
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
