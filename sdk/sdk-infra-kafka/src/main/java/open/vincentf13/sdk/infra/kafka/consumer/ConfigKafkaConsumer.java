package open.vincentf13.sdk.infra.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.infra.kafka.consumer.error.ErrorHandlerFactory;
import open.vincentf13.sdk.infra.kafka.consumer.record.RecordInterceptor;
import open.vincentf13.sdk.infra.kafka.KafkaEvent;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter;

/** Kafka Consumer 配置類 */
@Configuration
@EnableKafka
public class ConfigKafkaConsumer {

  private final org.springframework.kafka.listener.RecordInterceptor<Object, Object>
      recordInterceptor = new RecordInterceptor();

  @Bean
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
      ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
      ConsumerFactory<Object, Object> consumerFactory,
      KafkaTemplate<String, Object> kafkaTemplate,
      KafkaProperties kafkaProperties,
      ObjectMapper objectMapper) {

    return buildListenerContainerFactory(
        configurer, consumerFactory, kafkaTemplate, kafkaProperties, objectMapper, false, "SINGLE");
  }

  @Bean
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaBatchListenerContainerFactory(
      ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
      ConsumerFactory<Object, Object> consumerFactory,
      KafkaTemplate<String, Object> kafkaTemplate,
      KafkaProperties kafkaProperties,
      ObjectMapper objectMapper) {

    return buildListenerContainerFactory(
        configurer,
        consumerFactory,
        kafkaTemplate,
        kafkaProperties,
        objectMapper,
        true,
        "BATCH (Forced)");
  }

  private ConcurrentKafkaListenerContainerFactory<Object, Object> buildListenerContainerFactory(
      ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
      ConsumerFactory<Object, Object> consumerFactory,
      KafkaTemplate<String, Object> kafkaTemplate,
      KafkaProperties kafkaProperties,
      ObjectMapper objectMapper,
      boolean batchListener,
      String listenerTypeLabel) {

    ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    configurer.configure(factory, consumerFactory);
    factory.setBatchListener(batchListener);

    // 配置訊息轉換器：將 JSON String 轉換為 POJO (支援 Double Encoding)
    DoubleDecodingJsonMessageConverter converter =
        new DoubleDecodingJsonMessageConverter(objectMapper);
    factory.setRecordMessageConverter(converter);

    if (batchListener) {
      // 強制設定 BatchMessageConverter，確保 Converter 被正確使用
      factory.setBatchMessageConverter(new BatchMessagingMessageConverter(converter));
      factory.setRecordInterceptor(recordInterceptor);
    } else {
      // 僅對非 Batch 模式啟用 RecordInterceptor
      factory.setRecordInterceptor(recordInterceptor);
    }

    factory.setCommonErrorHandler(ErrorHandlerFactory.build(kafkaTemplate, kafkaProperties));

    OpenLog.info(
        KafkaEvent.KAFKA_CONSUMER_CONFIGURED,
        "ackMode",
        factory.getContainerProperties().getAckMode(),
        "listenerType",
        listenerTypeLabel,
        "errorHandler",
        "DLQ with 2 retries");

    return factory;
  }
}
