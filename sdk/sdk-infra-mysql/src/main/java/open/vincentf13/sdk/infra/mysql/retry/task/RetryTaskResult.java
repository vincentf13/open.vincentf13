package open.vincentf13.sdk.infra.mysql.retry.task;

import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskStatus;

public record RetryTaskResult<T>(RetryTaskStatus status, String message, T value) {
}
