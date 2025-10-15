package open.vincentf13.common.infra.kafka.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class ConfigKafkaProducer {

    @Bean(name = "kafkaByteArrayProducerFactory")
    @ConditionalOnMissingBean(name = "kafkaByteArrayProducerFactory")
    public ProducerFactory<String, byte[]> kafkaByteArrayProducerFactory(KafkaProperties properties) {
        Map<String, Object> config = properties.buildProducerProperties(null);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean(name = "kafkaByteArrayTemplate")
    @ConditionalOnMissingBean(name = "kafkaByteArrayTemplate")
    @ConditionalOnBean(name = "kafkaByteArrayProducerFactory")
    public KafkaTemplate<String, byte[]> kafkaByteArrayTemplate(
            @Qualifier("kafkaByteArrayProducerFactory") ProducerFactory<String, byte[]> producerFactory
                                                               ) {
        return new KafkaTemplate<>(producerFactory);
    }

}
