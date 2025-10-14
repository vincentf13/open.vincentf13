package open.vincentf13.common.infra.kafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.common.infra.kafka.producer.KafkaProducerHeaderResolver;
import open.vincentf13.common.infra.kafka.producer.KafkaProducerKeyResolver;
import open.vincentf13.common.infra.kafka.producer.KafkaProducerService;
import open.vincentf13.common.infra.kafka.producer.KafkaProducerServiceImpl;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaProducerAutoConfiguration {

    @Bean(name = "kafkaByteArrayProducerFactory")
    @ConditionalOnMissingBean(name = "kafkaByteArrayProducerFactory")
    public ProducerFactory<String, byte[]> kafkaByteArrayProducerFactory(KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties(null));
        config.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        // 傳輸可靠性
        config.put(ProducerConfig.ACKS_CONFIG, "all");            // 等待所有 ISR 確認
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 啟用冪等寫入，避免重送重複

        // 壓縮與批次
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd"); // 壓縮演算法
        config.put(ProducerConfig.LINGER_MS_CONFIG, 20);            // 等待聚合訊息 10~50ms，取中間值 20ms
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 131072);       // 批次大小 64~256KB，取 128KB
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864L); // 64MB 全域緩衝（建議同時設）

        // 重試與順序
        config.put(ProducerConfig.RETRIES_CONFIG, 10);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5); // 幂等下安全上限

        // 傳遞超時
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);


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
