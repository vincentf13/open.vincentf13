package open.vincentf13.common.infra.kafka.producer;

import java.util.function.Function;

/**
 * 允許呼叫端依照業務需求，由 payload 推導 Kafka key。
 */
@FunctionalInterface
public interface KafkaProducerKeyResolver extends Function<Object, String> {

    @Override
    String apply(Object message);
}
