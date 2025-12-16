package open.vincentf13.sdk.infra.mysql.pending.task;

import lombok.RequiredArgsConstructor;
import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
public class SysPendingTaskRescueService {
    
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);
    
    private final SysPendingTaskRepository repository;
    
    public int rescueProcessing(Duration timeout, String resultMsg) {
        Duration effectiveTimeout = (timeout == null || timeout.isZero() || timeout.isNegative())
                ? DEFAULT_TIMEOUT
                : timeout;
        Instant updatedBefore = Instant.now().minus(effectiveTimeout);
        return repository.rescueProcessingTimedOut(updatedBefore, resultMsg);
    }
}
