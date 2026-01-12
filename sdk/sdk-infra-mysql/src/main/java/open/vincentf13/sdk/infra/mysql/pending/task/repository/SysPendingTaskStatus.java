package open.vincentf13.sdk.infra.mysql.pending.task.repository;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SysPendingTaskStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAIL_RETRY,
    FAIL_TERMINAL
}
