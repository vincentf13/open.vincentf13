package open.vincentf13.common.infra.kafka.producer;

import java.util.Map;
import java.util.function.Function;

@FunctionalInterface
public interface KafkaProducerHeaderResolver extends Function<Object, Map<String, Object>> {

    @Override
    Map<String, Object> apply(Object message);
}
