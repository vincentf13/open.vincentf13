package open.vincentf13.sdk.infra.mysql.retry.task;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskPO;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskRepository;
import open.vincentf13.sdk.infra.mysql.retry.task.repository.RetryTaskStatus;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class RetryTaskService {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);
  private static final int MAX_RESULT_MSG_LENGTH = 200;

  private final RetryTaskRepository repository;

  /** 救援停在 PROCESSING 且超過 timeout 的任務，回到 FAIL_RETRY 以便重新排程。 */
  public int rescueProcessing(Duration timeout, String resultMsg) {
    Duration effectiveTimeout =
        (timeout == null || timeout.isZero() || timeout.isNegative()) ? DEFAULT_TIMEOUT : timeout;
    Instant updatedBefore = Instant.now().minus(effectiveTimeout);
    return repository.rescueProcessingTimedOut(updatedBefore, resultMsg);
  }

  @Transactional(rollbackFor = Exception.class)
  public <T> T handleTask(
      RetryTaskPO task, Duration retryDelay, Function<RetryTaskPO, RetryTaskResult<T>> processor) {
    try {
      RetryTaskResult<T> result = processor.apply(task);
      if (result == null || result.status() == RetryTaskStatus.SUCCESS) {
        repository.markSuccess(
            task.getId(),
            task.getVersion() + 1,
            normalizeMessage(result == null ? null : result.message(), "OK"));
        return result == null ? null : result.value();
      }
      if (result.status() == RetryTaskStatus.FAIL_TERMINAL) {
        repository.markFailTerminal(
            task.getId(), task.getVersion() + 1, normalizeMessage(result.message(), "terminal"));
        return result.value();
      }
      scheduleRetry(task, retryDelay, result.message());
      return result.value();
    } catch (Exception ex) {
      scheduleRetry(task, retryDelay, ex.getMessage());
      return null;
    }
  }

  private void scheduleRetry(RetryTaskPO task, Duration retryDelay, String reason) {
    int nextRetry = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
    int maxRetries = task.getMaxRetries() == null ? 3 : task.getMaxRetries();
    String msg = normalizeMessage(reason, "retry");
    if (nextRetry > maxRetries) {
      repository.markFailTerminal(task.getId(), task.getVersion() + 1, msg);
      return;
    }
    Duration delay = retryDelay != null ? retryDelay : Duration.ofSeconds(10);
    Instant nextRun = Instant.now().plus(delay);
    repository.markFailRetry(task.getId(), task.getVersion() + 1, msg, nextRun);
  }

  private String normalizeMessage(String message, String fallback) {
    if (message == null || message.isBlank()) {
      return fallback;
    }
    String trimmed = message.trim();
    return trimmed.length() > MAX_RESULT_MSG_LENGTH
        ? trimmed.substring(0, MAX_RESULT_MSG_LENGTH)
        : trimmed;
  }
}
