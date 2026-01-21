package open.vincentf13.sdk.infra.kafka.consumer.record;

import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.mapper.OpenObjectMapper;
import open.vincentf13.sdk.infra.kafka.KafkaEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class RecordInterceptor
    implements org.springframework.kafka.listener.RecordInterceptor<Object, Object> {

  @Override
  public ConsumerRecord<Object, Object> intercept(
      ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
    /** 若要開啟此處 debug 日誌，設定 logging.level.open.vincentf13.sdk.infra.kafka=DEBUG。 */
    String summary =
        String.format(
            "topic=%s partition=%d offset=%d listenerId=%s",
            record.topic(), record.partition(), record.offset(), Thread.currentThread().getName());
    String eventJson = decodeEventJson(record.value());
    OpenLog.debug(
        KafkaEvent.KAFKA_CONSUME_DEBUG, "detail", "\n" + summary + "\n" + "event=" + eventJson);
    return record;
  }

  private String decodeEventJson(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String stringValue) {
      String trimmed = stringValue.trim();
      String json = stringValue;
      if (trimmed.startsWith("\"")) {
        try {
          json = OpenObjectMapper.fromJson(trimmed, String.class);
        } catch (RuntimeException ex) {
          json = stringValue;
        }
      }
      try {
        return OpenObjectMapper.toJson(OpenObjectMapper.readTree(json));
      } catch (RuntimeException ex) {
        return json;
      }
    }
    return OpenObjectMapper.toJson(value);
  }

  @Override
  public void success(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
    OpenLog.debug(
        KafkaEvent.KAFKA_CONSUME_DEBUG,
        "topic",
        record.topic(),
        "partition",
        record.partition(),
        "offset",
        record.offset(),
        "listenerId",
        Thread.currentThread().getName());
  }

  @Override
  public void failure(
      ConsumerRecord<Object, Object> record,
      Exception exception,
      Consumer<Object, Object> consumer) {
    OpenLog.warn(
        KafkaEvent.KAFKA_CONSUME_ERROR,
        exception,
        "topic",
        record.topic(),
        "partition",
        record.partition(),
        "offset",
        record.offset(),
        "listenerId",
        Thread.currentThread().getName());
  }
}
