package open.vincentf13.sdk.infra.kafka.config;

import open.vincentf13.sdk.infra.kafka.consumer.KafkaConsumerResetService;
import open.vincentf13.sdk.infra.kafka.consumer.KafkaDebugOffsetResetConfiguration;
import open.vincentf13.sdk.infra.kafka.consumer.controller.KafkaAdminController;
import open.vincentf13.sdk.infra.kafka.producer.ConfigKafkaProducer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

@AutoConfiguration
@Import({
    KafkaConsumerResetService.class,
    ConfigKafkaProducer.class,
    KafkaDebugOffsetResetConfiguration.class
})
public class SdkInfraKafkaAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication
    public KafkaAdminController kafkaAdminController(KafkaListenerEndpointRegistry registry) {
        return new KafkaAdminController(registry);
    }
}
