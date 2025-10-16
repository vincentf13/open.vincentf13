package open.vincentf13.common.infra.kafka.config;

import open.vincentf13.common.core.log.OpenLog;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Kafka Consumer 推薦配置類。
 * <p>
 * 這個配置類提供了以下最佳實踐：
 * <ul>
 *     <li>啟用 Spring Kafka 功能。</li>
 *     <li>配置為批次消費 (Batch Listening)。</li>
 *     <li>配置手動 ACK (Manual Acknowledgement)。</li>
 *     <li>配置重試機制和死信隊列 (DLQ)。</li>
 * </ul>
 * 下游服務只需引入此模塊，即可自動獲得這些配置。
 */
@Configuration
@EnableKafka
@ConditionalOnBean(ConsumerFactory.class)
public class ConfigKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ConfigKafkaConsumer.class);


    @Bean
    public ConsumerFactory<String, String> consumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());

        // 為每個併發 consumer 生成唯一靜態 ID
        String instanceId = "consumer-" + UUID.randomUUID();  // 或以環境變數、Pod 名稱替代
        props.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, instanceId);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * 創建並配置 Kafka 監聽器容器工廠。
     *
     * @param consumerFactory Spring Boot 自動配置的 ConsumerFactory。
     * @param kafkaTemplate   Spring Boot 自動配置的 KafkaTemplate，用於發送訊息到 DLQ。
     * @param kafkaProperties Spring Boot 自動配置的 Kafka 屬性，用於日誌記錄。
     * @return 配置好的 ConcurrentKafkaListenerContainerFactory。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]>

    kafkaListenerContainerFactory(
            ConsumerFactory<String, byte[]> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate,
            KafkaProperties kafkaProperties) {

        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // 配置為批次拉取
        factory.setBatchListener(true);

        // 配置為手動 ACK
        factory.getContainerProperties().setAckMode(AckMode.MANUAL);

        // 批次轉換：byte[] → JSON → DTO 列表
        factory.setBatchMessageConverter(
                new BatchMessagingMessageConverter(new ByteArrayJsonMessageConverter())
                                        );
        // 配置錯誤處理器 (重試 + DLQ)
        // 創建 DeadLetterPublishingRecoverer，當重試耗盡時，將訊息發送到 DLQ
        // DLQ 的 topic 命名規則為：<原始topic>.DLT
        BiFunction<ConsumerRecord<?,?>, Exception, TopicPartition> dlqTopicRouter = (record, ex) ->new TopicPartition(record.topic() + ".DLT", record.partition());
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, dlqTopicRouter);

        // 創建 DefaultErrorHandler，設置重試次數和間隔
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
                     "ackMode", "MANUAL",
                     "listenerType", "BATCH",
                     "errorHandler", "DLQ with 2 retries");

        return factory;
    }
}
