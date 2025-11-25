package open.vincentf13.sdk.core.log;

/*
 * 預設事件定義，可依需求擴充。
 */
public enum OpenEventEnum implements OpenEvent {
    REQUEST_RECEIVED("RequestReceived", "HTTP request received"),
    RESPONSE_SENT("ResponseSent", "HTTP response sent"),
    KAFKA_SEND("KafkaSend", "Kafka message send"),
    KAFKA_CONSUME("KafkaConsume", "Kafka message consume");

    private final String event;
    private final String message;

    OpenEventEnum(String event, String message) {
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
