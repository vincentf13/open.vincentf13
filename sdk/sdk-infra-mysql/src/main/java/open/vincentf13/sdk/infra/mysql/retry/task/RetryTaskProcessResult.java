package open.vincentf13.sdk.infra.mysql.retry.task;

import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskStatus;

public record RetryTaskProcessResult(RetryTaskStatus status, String message) {
    public static RetryTaskProcessResult success(String message) {
        return new RetryTaskProcessResult(RetryTaskStatus.SUCCESS, message);
    }

    public static RetryTaskProcessResult retry(String message) {
        return new RetryTaskProcessResult(RetryTaskStatus.FAIL_RETRY, message);
    }

    public static RetryTaskProcessResult terminal(String message) {
        return new RetryTaskProcessResult(RetryTaskStatus.FAIL_TERMINAL, message);
    }
}
