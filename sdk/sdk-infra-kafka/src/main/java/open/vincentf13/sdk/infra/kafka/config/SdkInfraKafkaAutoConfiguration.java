package open.vincentf13.sdk.infra.kafka.config;

import open.vincentf13.sdk.infra.kafka.producer.ConfigKafkaProducer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({ConfigKafkaProducer.class})
public class SdkInfraKafkaAutoConfiguration {}
