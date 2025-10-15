package open.vincentf13.common.infra.kafka.producer;

import java.util.Map;
import java.util.function.Function;

/**
 * 允許呼叫端為每筆 payload 動態產生 Kafka headers。
 *
 * <pre>{@code
 * KafkaProducerHeaderResolver headerResolver = payload -> Map.of(
 *         "x-request-id", RequestContext.currentRequestId(),
 *         "event-type", ((DomainEvent) payload).type()
 * );
 * }
 * </pre>
 */
@FunctionalInterface
public interface KafkaProducerHeaderResolver extends Function<Object, Map<String, Object>> {

    @Override
    Map<String, Object> apply(Object message);
}
