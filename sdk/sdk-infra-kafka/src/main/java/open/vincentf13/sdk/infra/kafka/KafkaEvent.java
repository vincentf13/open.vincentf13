package open.vincentf13.sdk.infra.kafka;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
  Kafka 事件枚舉。
 */
public enum KafkaEvent implements OpenEvent {
    KAFKA_SEND("KafkaSend", "送出 Kafka 訊息"),
    KAFKA_SEND_FAILED("KafkaSendFailed", "Kafka 訊息送出失敗"),
    KAFKA_HEADER_ENCODE_FAILED("KafkaHeaderEncodeFailed", "Kafka header 序列化失敗"),
    KAFKA_SEND_ERROR("KafkaSendError", "Kafka 訊息送出失敗"),
    KAFKA_SEND_SUCCESS("KafkaSendSuccess", "Kafka 訊息送出成功"),
    KAFKA_CONSUME_RETRY("KafkaConsumeRetry", "Kafka 訊息消費重試"),
    KAFKA_CONSUMER_CONFIGURED("KafkaConsumerConfigured", "自定義 Kafka Consumer 配置已加載");

    private final String event;
    private final String message;

    KafkaEvent(String event, String message) {
        this.event = event;
        this.message = message;
    }

    @Override
    public String event() {
        return event;
    }

    @Override
    public String message() {
        return message;
    }
}
