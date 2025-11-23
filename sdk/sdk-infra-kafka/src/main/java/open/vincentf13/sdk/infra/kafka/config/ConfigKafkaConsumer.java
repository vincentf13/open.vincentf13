package open.vincentf13.sdk.infra.kafka.config;

import open.vincentf13.sdk.core.OpenLog;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.function.BiFunction;

/**
 * Kafka Consumer 配置類
 */
@Configuration
@EnableKafka
@ConditionalOnBean(ConsumerFactory.class)
public class ConfigKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ConfigKafkaConsumer.class);


    /**
     * 創建並配置 Kafka 監聽器容器工廠。
     *
     * @param consumerFactory Spring Boot 自動配置的 ConsumerFactory。
     * @param kafkaTemplate   Spring Boot 自動配置的 KafkaTemplate，用於發送訊息到 DLQ。
     * @param kafkaProperties Spring Boot 自動配置的 Kafka 屬性，用於日誌記錄。
     * @return 配置好的 ConcurrentKafkaListenerContainerFactory。
     */
    @Bean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object>  consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaProperties kafkaProperties) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        // 套用 spring.kafka.listener.* 與 spring.kafka.consumer.* 配置
        configurer.configure(factory, consumerFactory);


        // 配置錯誤處理器 (重試 + DLQ)
        // 創建 DeadLetterPublishingRecoverer，當重試耗盡時，將訊息發送到 DLQ
        // DLQ 的 topic 命名規則為：<原始topic>.DLT
        BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> dlqTopicRouter = (record, ex) ->new TopicPartition(record.topic() + ".DLT", record.partition());
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, dlqTopicRouter);

        // 設置重試次數和間隔
        // listener 方法裡未捕捉的 runtime / checked exception 都會重試
        // 重試耗盡後 DeadLetterPublishingRecoverer 把原訊息送到 <topic>.DLT，Recoverer 成功回傳後 DefaultErrorHandler 才告知容器「這筆已處理完成」，容器再提交原 Topic 的 offset。
        // 此處配置為重試 2 次，每次間隔 1 秒。之後再發送到 DLQ。
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));

        // 註冊日誌記錄，以便觀察重試和 DLQ 的行為
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                                               OpenLog.warn(log, "KafkaConsumeRetry", "Kafka 訊息消費重試", ex,
                                                            "topic", record.topic(),
                                                            "partition", record.partition(),
                                                            "offset", record.offset(),
                                                            "attempt", deliveryAttempt,
                                                            "groupId", kafkaProperties.getConsumer().getGroupId())
                                      );

        factory.setCommonErrorHandler(errorHandler);

        OpenLog.info(log, "KafkaConsumerConfigured", "自定義 Kafka Consumer 配置已加載",
                     "ackMode", factory.getContainerProperties().getAckMode(),
                     "listenerType", factory.isBatchListener() ? "BATCH" : "SINGLE" ,
                     "errorHandler", "DLQ with 2 retries");

        return factory;
    }
}
