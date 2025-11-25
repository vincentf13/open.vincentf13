package open.vincentf13.sdk.core;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * Core 模組事件。
 */
public enum CoreEventEnum implements OpenEvent {
    DEFAULTS_RESOURCE_MISSING("DefaultsResourceMissing", "Defaults resource not found"),
    DEFAULTS_APPLIED("DefaultsApplied", "Defaults applied from resource");

    private final String event;
    private final String message;

    CoreEventEnum(String event, String message) {
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
