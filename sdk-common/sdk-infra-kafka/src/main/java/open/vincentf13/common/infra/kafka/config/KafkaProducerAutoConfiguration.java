package open.vincentf13.common.infra.kafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.function.Function;

import open.vincentf13.common.infra.kafka.producer.KafkaProducerHeaderResolver;
import open.vincentf13.common.infra.kafka.producer.KafkaProducerKeyResolver;
import open.vincentf13.common.infra.kafka.producer.KafkaProducerService;
import open.vincentf13.common.infra.kafka.producer.KafkaProducerServiceImpl;
import org.springframework.beans.factory.ObjectProvider;
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

@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaProducerAutoConfiguration {

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

    @Bean
    @ConditionalOnBean(name = "kafkaByteArrayTemplate")
    @ConditionalOnMissingBean(KafkaProducerService.class)
    public KafkaProducerService<Object> kafkaProducerService(
            @Qualifier("kafkaByteArrayTemplate") KafkaTemplate<String, byte[]> kafkaTemplate,
            @Qualifier("jsonMapper") ObjectMapper objectMapper,
            ObjectProvider<KafkaProducerKeyResolver> keyResolverProvider,
            ObjectProvider<KafkaProducerHeaderResolver> headerResolverProvider
                                                            ) {
        // 允許下游模組自行註冊 key/header 解析器，覆寫預設行為。
        Function<Object, String> defaultKey = keyResolverProvider.getIfAvailable();
        Function<Object, Map<String, Object>> headerFunction = headerResolverProvider.getIfAvailable();
        return new KafkaProducerServiceImpl<>(kafkaTemplate, objectMapper, defaultKey, headerFunction);
    }
}
