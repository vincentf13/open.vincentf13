package open.vincentf13.sdk.infra.mysql;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * MySQL 模組事件。
 */
public enum MysqlEvent implements OpenEvent {
    MAPPER_SQL_DEBUG_ENABLED("MapperSqlDebugEnabled", "Enabled DEBUG logging for mapper package");

    private final String event;
    private final String message;

    MysqlEvent(String event, String message) {
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
