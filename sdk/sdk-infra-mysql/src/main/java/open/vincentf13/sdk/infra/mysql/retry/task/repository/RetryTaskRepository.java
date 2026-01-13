package open.vincentf13.sdk.infra.mysql.retry.task.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.util.Assert;

@RequiredArgsConstructor
public class RetryTaskRepository {

  private static final int DEFAULT_MAX_RETRIES = 3;
  private static final int DEFAULT_PRIORITY = 10;
  private static final int DEFAULT_FETCH_LIMIT = 10;
  private static final String RESCUE_REASON = "Task timed out (Worker crashed?)";

  private final RetryTaskMapper mapper;

  public <T> RetryTaskPO insertPendingTask(
      Enum<?> bizType,
      String bizKey,
      T payload,
      Integer maxRetries,
      Integer priority,
      Instant nextRunTime) {
    String type = bizType == null ? null : bizType.name();
    Assert.hasText(type, "bizType must not be blank");
    Assert.hasText(bizKey, "bizKey must not be blank");

    RetryTaskPO record =
        RetryTaskPO.builder()
            .bizType(type)
            .bizKey(bizKey)
            .status(RetryTaskStatus.PENDING)
            .priority(priority != null ? priority : DEFAULT_PRIORITY)
            .payload(payload == null ? null : OpenObjectMapper.toJson(payload))
            .retryCount(0)
            .maxRetries(maxRetries != null ? maxRetries : DEFAULT_MAX_RETRIES)
            .nextRunTime(nextRunTime != null ? nextRunTime : Instant.now().plusSeconds(10))
            .version(0)
            .build();
    mapper.insert(record);
    return record;
  }

  public <T> RetryTaskPO insertPendingTask(Enum<?> bizType, String bizKey, T payload) {
    /** 使用預設 maxRetries/priority/nextRunTime 建立 retry_task 紀錄。 */
    return insertPendingTask(bizType, bizKey, payload, null, null, null);
  }

  public List<RetryTaskPO> findPending(int limit) {
    int fetchLimit = limit > 0 ? limit : DEFAULT_FETCH_LIMIT;
    LambdaQueryWrapper<RetryTaskPO> wrapper = new LambdaQueryWrapper<>();
    wrapper
        .in(RetryTaskPO::getStatus, RetryTaskStatus.PENDING, RetryTaskStatus.FAIL_RETRY)
        .le(RetryTaskPO::getNextRunTime, Instant.now())
        .orderByAsc(RetryTaskPO::getPriority, RetryTaskPO::getId)
        .last("LIMIT " + fetchLimit);
    return mapper.selectList(wrapper);
  }

  public RetryTaskPO findById(Long id) {
    Assert.notNull(id, "id must not be null");
    return mapper.selectById(id);
  }

  public boolean tryMarkProcessing(Long id, Integer version) {
    Assert.notNull(id, "id must not be null");
    Assert.notNull(version, "version must not be null");
    LambdaUpdateWrapper<RetryTaskPO> wrapper = new LambdaUpdateWrapper<>();
    wrapper
        .set(RetryTaskPO::getStatus, RetryTaskStatus.PROCESSING)
        .setSql("version = version + 1")
        .eq(RetryTaskPO::getId, id)
        .eq(RetryTaskPO::getVersion, version);
    return mapper.update(null, wrapper) == 1;
  }

  public boolean markSuccess(Long id, Integer version, String resultMsg) {
    Assert.notNull(id, "id must not be null");
    Assert.notNull(version, "version must not be null");
    LambdaUpdateWrapper<RetryTaskPO> wrapper = new LambdaUpdateWrapper<>();
    wrapper
        .set(RetryTaskPO::getStatus, RetryTaskStatus.SUCCESS)
        .set(RetryTaskPO::getResultMsg, resultMsg)
        .setSql("version = version + 1")
        .eq(RetryTaskPO::getId, id)
        .eq(RetryTaskPO::getVersion, version);
    return mapper.update(null, wrapper) == 1;
  }

  public boolean markFailRetry(Long id, Integer version, String resultMsg, Instant nextRunTime) {
    Assert.notNull(id, "id must not be null");
    Assert.notNull(version, "version must not be null");
    Instant scheduleAt = nextRunTime != null ? nextRunTime : Instant.now();
    LambdaUpdateWrapper<RetryTaskPO> wrapper = new LambdaUpdateWrapper<>();
    wrapper
        .set(RetryTaskPO::getStatus, RetryTaskStatus.FAIL_RETRY)
        .setSql("retry_count = retry_count + 1")
        .set(RetryTaskPO::getNextRunTime, scheduleAt)
        .set(RetryTaskPO::getResultMsg, resultMsg)
        .setSql("version = version + 1")
        .eq(RetryTaskPO::getId, id)
        .eq(RetryTaskPO::getVersion, version);
    return mapper.update(null, wrapper) == 1;
  }

  public boolean markFailTerminal(Long id, Integer version, String resultMsg) {
    Assert.notNull(id, "id must not be null");
    Assert.notNull(version, "version must not be null");
    LambdaUpdateWrapper<RetryTaskPO> wrapper = new LambdaUpdateWrapper<>();
    wrapper
        .set(RetryTaskPO::getStatus, RetryTaskStatus.FAIL_TERMINAL)
        .set(RetryTaskPO::getResultMsg, resultMsg)
        .setSql("version = version + 1")
        .eq(RetryTaskPO::getId, id)
        .eq(RetryTaskPO::getVersion, version);
    return mapper.update(null, wrapper) == 1;
  }

  public int rescueProcessingTimedOut(Instant updatedBefore, String resultMsg) {
    Assert.notNull(updatedBefore, "updatedBefore must not be null");
    String msg = resultMsg != null ? resultMsg : RESCUE_REASON;
    LambdaUpdateWrapper<RetryTaskPO> wrapper = new LambdaUpdateWrapper<>();
    wrapper
        .set(RetryTaskPO::getStatus, RetryTaskStatus.FAIL_RETRY)
        .setSql("retry_count = retry_count + 1")
        .set(RetryTaskPO::getResultMsg, msg)
        .setSql("version = version + 1")
        .set(RetryTaskPO::getNextRunTime, Instant.now())
        .eq(RetryTaskPO::getStatus, RetryTaskStatus.PROCESSING)
        .le(RetryTaskPO::getUpdatedAt, updatedBefore);
    return mapper.update(null, wrapper);
  }
}
