package open.vincentf13.common.infra.kafka.producer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.support.SendResult;

/**
 * Generic Kafka producer abstraction that always serializes payloads to bytes.
 */
public interface KafkaProducerService<T> {

    CompletableFuture<SendResult<String, byte[]>> send(String topic, String key, T msg, Map<String, Object> headers);

    CompletableFuture<SendResult<String, byte[]>> send(String topic, T msg);

    CompletableFuture<List<SendResult<String, byte[]>>> sendBatch(String topic, Collection<? extends T> msgs);
}
