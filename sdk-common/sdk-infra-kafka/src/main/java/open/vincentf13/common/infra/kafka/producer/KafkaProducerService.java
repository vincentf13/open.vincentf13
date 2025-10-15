package open.vincentf13.common.infra.kafka.producer;

import org.springframework.kafka.support.SendResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 泛型 Kafka Producer 介面：所有訊息皆序列化為 bytes 傳送。
 */
public interface KafkaProducerService<T> {

    CompletableFuture<SendResult<String, byte[]>> send(String topic, String key, T msg, Map<String, Object> headers);

    CompletableFuture<SendResult<String, byte[]>> send(String topic, T msg);

    default CompletableFuture<List<SendResult<String, byte[]>>> sendBatch(String topic, Collection<? extends T> msgs) {
        return sendBatch(topic, msgs, null, null);
    }

    /**
     * 批次送出訊息，可於此處提供客製 key 與 header 解析器。
     */
    CompletableFuture<List<SendResult<String, byte[]>>> sendBatch(
            String topic,
            Collection<? extends T> msgs,
            Function<Object, String> keyResolver,
            Function<Object, Map<String, Object>> headerResolver);
}
