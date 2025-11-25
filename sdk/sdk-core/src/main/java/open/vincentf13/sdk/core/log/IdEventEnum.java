package open.vincentf13.sdk.core.log;

/*
 * 核心/ID 相關事件枚舉。
 */
public enum IdEventEnum implements OpenEvent {
    BEAN_CONFIG("BeanConfig", "DefaultIdGenerator");

    private final String event;
    private final String message;

    IdEventEnum(String event, String message) {
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
