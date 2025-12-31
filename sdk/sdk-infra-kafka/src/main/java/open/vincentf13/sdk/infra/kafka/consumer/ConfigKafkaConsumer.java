package open.vincentf13.sdk.infra.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.infra.kafka.KafkaEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter;
import org.springframework.util.backoff.FixedBackOff;

import java.util.function.BiFunction;

/**
 Kafka Consumer 配置類
 */
@Configuration
@EnableKafka
public class ConfigKafkaConsumer {

    private static final long RETRY_BACKOFF_MS = 1000L;
    private static final long RETRY_MAX_ATTEMPTS = 2L;
    private static final String DLT_SUFFIX = ".DLT";

    @Bean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper) {

        return buildListenerContainerFactory(configurer, consumerFactory, kafkaTemplate, kafkaProperties, objectMapper, false, "SINGLE");
    }

    @Bean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaBatchListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper) {

        return buildListenerContainerFactory(configurer, consumerFactory, kafkaTemplate, kafkaProperties, objectMapper, true, "BATCH (Forced)");
    }

    private ConcurrentKafkaListenerContainerFactory<Object, Object> buildListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper,
            boolean batchListener,
            String listenerTypeLabel) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setBatchListener(batchListener);

        // 配置訊息轉換器：將 JSON String 轉換為 POJO (支援 Double Encoding)
        DoubleDecodingJsonMessageConverter converter = new DoubleDecodingJsonMessageConverter(objectMapper);
        factory.setRecordMessageConverter(converter);
        
        if (batchListener) {
            // 強制設定 BatchMessageConverter，確保 Converter 被正確使用
            factory.setBatchMessageConverter(new BatchMessagingMessageConverter(converter));
        }

        factory.setCommonErrorHandler(buildErrorHandler(kafkaTemplate, kafkaProperties));

        OpenLog.info(KafkaEvent.KAFKA_CONSUMER_CONFIGURED,
                     "ackMode", factory.getContainerProperties().getAckMode(),
                     "listenerType", listenerTypeLabel,
                     "errorHandler", "DLQ with 2 retries");

        return factory;
    }

    private DefaultErrorHandler buildErrorHandler(KafkaTemplate<String, Object> kafkaTemplate,
                                                  KafkaProperties kafkaProperties) {
        BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> dlqTopicRouter = 
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition());
        
        DeadLetterPublishingRecoverer dlqRecoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, dlqTopicRouter);
        
        // 包裝 Recoverer 以確保在最終失敗（包含 Fatal Exception 不重試的情況）時印出 Stack Trace
        ConsumerRecordRecoverer loggingRecoverer = (record, ex) -> {
            OpenLog.warn(KafkaEvent.KAFKA_CONSUME_FAILED, ex,
                          "topic", record.topic(),
                          "partition", record.partition(),
                          "offset", record.offset());
            
            dlqRecoverer.accept(record, ex);
        };

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(loggingRecoverer, new FixedBackOff(RETRY_BACKOFF_MS, RETRY_MAX_ATTEMPTS));
        
 
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            OpenLog.warn(KafkaEvent.KAFKA_CONSUME_RETRY,
                          "topic", record.topic(),
                          "partition", record.partition(),
                          "offset", record.offset(),
                          "attempt", deliveryAttempt,
                          "groupId", kafkaProperties.getConsumer().getGroupId());
        });
        return errorHandler;
    }
}
