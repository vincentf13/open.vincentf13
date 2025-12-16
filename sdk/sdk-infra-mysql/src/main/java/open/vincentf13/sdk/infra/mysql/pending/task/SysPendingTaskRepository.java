package open.vincentf13.sdk.infra.mysql.pending.task;

import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public class SysPendingTaskRepository {
    
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_PRIORITY = 10;
    private static final int DEFAULT_FETCH_LIMIT = 10;
    private static final String RESCUE_REASON = "Task timed out (Worker crashed?)";
    
    private final SysPendingTaskMapper mapper;
    
    public <T> SysPendingTaskPO insert(String bizType,
                                       String bizKey,
                                       T payload,
                                       Integer maxRetries,
                                       Integer priority,
                                       Instant nextRunTime) {
        Assert.hasText(bizType, "bizType must not be blank");
        Assert.hasText(bizKey, "bizKey must not be blank");
        
        SysPendingTaskPO record = SysPendingTaskPO.builder()
                                                  .bizType(bizType)
                                                  .bizKey(bizKey)
                                                  .status(SysPendingTaskStatus.PENDING)
                                                  .priority(priority != null ? priority : DEFAULT_PRIORITY)
                                                  .payload(payload == null ? null : OpenObjectMapper.toJson(payload))
                                                  .retryCount(0)
                                                  .maxRetries(maxRetries != null ? maxRetries : DEFAULT_MAX_RETRIES)
                                                  .nextRunTime(nextRunTime != null ? nextRunTime : Instant.now())
                                                  .version(0)
                                                  .build();
        mapper.insert(record);
        return record;
    }
    
    public List<SysPendingTaskPO> fetchRunnable(int limit) {
        int fetchLimit = limit > 0 ? limit : DEFAULT_FETCH_LIMIT;
        return mapper.fetchRunnable(fetchLimit);
    }
    
    public boolean tryMarkProcessing(Long id, Integer version) {
        Assert.notNull(id, "id must not be null");
        Assert.notNull(version, "version must not be null");
        return mapper.markProcessing(id, version) == 1;
    }
    
    public boolean markSuccess(Long id, Integer version, String resultMsg) {
        Assert.notNull(id, "id must not be null");
        Assert.notNull(version, "version must not be null");
        return mapper.markSuccess(id, version, resultMsg) == 1;
    }
    
    public boolean markFailRetry(Long id, Integer version, String resultMsg, Instant nextRunTime) {
        Assert.notNull(id, "id must not be null");
        Assert.notNull(version, "version must not be null");
        Instant scheduleAt = nextRunTime != null ? nextRunTime : Instant.now();
        return mapper.markFailRetry(id, version, resultMsg, scheduleAt) == 1;
    }
    
    public boolean markFailTerminal(Long id, Integer version, String resultMsg) {
        Assert.notNull(id, "id must not be null");
        Assert.notNull(version, "version must not be null");
        return mapper.markFailTerminal(id, version, resultMsg) == 1;
    }
    
    public int rescueProcessingTimedOut(Instant updatedBefore, String resultMsg) {
        Assert.notNull(updatedBefore, "updatedBefore must not be null");
        String msg = resultMsg != null ? resultMsg : RESCUE_REASON;
        return mapper.rescueStuck(updatedBefore, msg);
    }
}
