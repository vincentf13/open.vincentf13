package open.vincentf13.sdk.infra.mysql.retry.task;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class RetryTaskRescueScheduler {

  private final RetryTaskService rescueService;
  private final Duration timeout;

  @Scheduled(fixedDelayString = "${open.vincentf13.retry-task.rescue.fixed-delay:60000}")
  public void rescue() {
    int rescued = rescueService.rescueProcessing(timeout, null);
    if (rescued > 0 && log.isInfoEnabled()) {
      log.info("RetryTask rescue: {} tasks moved back to FAIL_RETRY", rescued);
    }
  }
}
