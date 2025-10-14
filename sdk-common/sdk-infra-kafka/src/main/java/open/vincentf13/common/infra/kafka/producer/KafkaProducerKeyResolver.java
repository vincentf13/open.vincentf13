package open.vincentf13.common.infra.kafka.producer;

import java.util.function.Function;

@FunctionalInterface
public interface KafkaProducerKeyResolver extends Function<Object, String> {

    @Override
    String apply(Object message);
}
