package open.vincentf13.sdk.infra.kafka.consumer.error;

import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.infra.kafka.KafkaEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
public final class ErrorHandlerFactory {

  private static final long RETRY_BACKOFF_MS = 1000L;
  private static final long RETRY_MAX_ATTEMPTS = 2L;
  private static final String DLT_SUFFIX = ".DLT";

  private ErrorHandlerFactory() {}

  public static DefaultErrorHandler build(
      KafkaTemplate<String, Object> kafkaTemplate, KafkaProperties kafkaProperties) {
    BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> dlqTopicRouter =
        (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition());

    DeadLetterPublishingRecoverer dlqRecoverer =
        new DeadLetterPublishingRecoverer(kafkaTemplate, dlqTopicRouter);

    ConsumerRecordRecoverer loggingRecoverer =
        (record, ex) -> {
          OpenLog.warn(
              log,
              KafkaEvent.KAFKA_CONSUME_FAILED,
              "topic",
              record.topic(),
              "partition",
              record.partition(),
              "offset",
              record.offset());

          dlqRecoverer.accept(record, ex);
        };

    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(
            loggingRecoverer, new FixedBackOff(RETRY_BACKOFF_MS, RETRY_MAX_ATTEMPTS));

    errorHandler.setRetryListeners(
        (record, ex, deliveryAttempt) -> {
          OpenLog.warn(
              log,
              KafkaEvent.KAFKA_CONSUME_RETRY,
              "topic",
              record.topic(),
              "partition",
              record.partition(),
              "offset",
              record.offset(),
              "attempt",
              deliveryAttempt,
              "groupId",
              kafkaProperties.getConsumer().getGroupId());
        });
    return errorHandler;
  }
}
