package open.vincentf13.sdk.infra.kafka.config;

import open.vincentf13.sdk.infra.kafka.consumer.KafkaConsumerResetService;
import open.vincentf13.sdk.infra.kafka.producer.ConfigKafkaProducer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    KafkaConsumerResetService.class,
    ConfigKafkaProducer.class
})
public class SdkInfraKafkaAutoConfiguration {
}
