package open.vincentf13.sdk.infra.mysql.retry.task.repository;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RetryTaskStatus {
  PENDING,
  PROCESSING,
  SUCCESS,
  FAIL_RETRY,
  FAIL_TERMINAL
}
