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
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 Kafka Consumer 配置類
 */
@Configuration
@EnableKafka
public class ConfigKafkaConsumer {

    private static final long RETRY_BACKOFF_MS = 1000L;
    private static final long RETRY_MAX_ATTEMPTS = 2L;
    private static final String DLT_SUFFIX = ".DLT";

    /**
     創建並配置 Kafka 監聽器容器工廠。
     
     @param consumerFactory Spring Boot 自動配置的 ConsumerFactory。
     @param kafkaTemplate   Spring Boot 自動配置的 KafkaTemplate，用於發送訊息到 DLQ。
     @param kafkaProperties Spring Boot 自動配置的 Kafka 屬性，用於日誌記錄。
     @param objectMapper    Spring Boot 自動配置的 ObjectMapper。
     @return 配置好的 ConcurrentKafkaListenerContainerFactory。
     */
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
        // 套用 spring.kafka.listener.* 與 spring.kafka.consumer.* 配置
        configurer.configure(factory, consumerFactory);
        factory.setBatchListener(batchListener);

        // 配置訊息轉換器：將 JSON String 轉換為 POJO (支援 Double Encoding)
        factory.setRecordMessageConverter(new DoubleDecodingJsonMessageConverter(objectMapper));

        factory.setCommonErrorHandler(buildErrorHandler(kafkaTemplate, kafkaProperties));

        OpenLog.info(KafkaEvent.KAFKA_CONSUMER_CONFIGURED,
                     "ackMode", factory.getContainerProperties().getAckMode(),
                     "listenerType", listenerTypeLabel,
                     "errorHandler", "DLQ with 2 retries");

        return factory;
    }

    private DefaultErrorHandler buildErrorHandler(KafkaTemplate<String, Object> kafkaTemplate,
                                                  KafkaProperties kafkaProperties) {
        // 配置錯誤處理器 (重試 + DLQ)
        // 重試耗盡後 DeadLetterPublishingRecoverer 把原訊息送到 <topic>.DLT
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, this::routeToDlq);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_BACKOFF_MS, RETRY_MAX_ATTEMPTS));
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                                               OpenLog.error(KafkaEvent.KAFKA_CONSUME_RETRY, ex,
                                                            "topic", record.topic(),
                                                            "partition", record.partition(),
                                                            "offset", record.offset(),
                                                            "attempt", deliveryAttempt,
                                                            "groupId", kafkaProperties.getConsumer().getGroupId(),
                                                            "stackTrace", stackTraceOf(ex))
                                      );
        return errorHandler;
    }

    private TopicPartition routeToDlq(ConsumerRecord<?, ?> record, Exception ex) {
        return new TopicPartition(record.topic() + DLT_SUFFIX, record.partition());
    }

    private String stackTraceOf(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        PrintWriter printer = new PrintWriter(writer);
        throwable.printStackTrace(printer);
        printer.flush();
        return writer.toString();
    }
}
