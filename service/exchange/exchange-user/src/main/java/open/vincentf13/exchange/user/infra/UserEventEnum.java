package open.vincentf13.exchange.user.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * User 模組事件。
 */
public enum UserEventEnum implements OpenEvent {
    AUTH_CREDENTIAL_PERSIST_FAILED("UserAuthCredentialPersistFailed", "Failed to persist auth credential"),
    AUTH_CREDENTIAL_RETRY_ERROR("UserAuthCredentialRetryError", "Unexpected error while retrying credential creation"),
    AUTH_CREDENTIAL_RETRY_SUCCESS("UserAuthCredentialRetrySuccess", "Successfully synchronized auth credential"),
    AUTH_CREDENTIAL_RETRY_EXCEEDED("UserAuthCredentialRetryExceeded", "Exceeded retry attempts"),
    AUTH_CREDENTIAL_RETRY_SCHEDULED("UserAuthCredentialRetryScheduled", "Scheduled credential retry"),
    AUTH_CREDENTIAL_RETRY_JOB_FAILED("UserAuthCredentialRetryJobFailed", "Retry job failed");

    private final String event;
    private final String message;

    UserEventEnum(String event, String message) {
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
