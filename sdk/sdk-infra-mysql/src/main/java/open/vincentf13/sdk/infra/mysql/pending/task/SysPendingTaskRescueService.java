package open.vincentf13.sdk.infra.mysql.pending.task;

import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.infra.mysql.pending.task.repository.SysPendingTaskRepository;

import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
public class SysPendingTaskRescueService {
    
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);
    
    private final SysPendingTaskRepository repository;
    
    /**
      救援停在 PROCESSING 且超過 timeout 的任務，回到 FAIL_RETRY 以便重新排程。
     */
    public int rescueProcessing(Duration timeout, String resultMsg) {
        Duration effectiveTimeout = (timeout == null || timeout.isZero() || timeout.isNegative())
                ? DEFAULT_TIMEOUT
                : timeout;
        Instant updatedBefore = Instant.now().minus(effectiveTimeout);
        return repository.rescueProcessingTimedOut(updatedBefore, resultMsg);
    }
}
