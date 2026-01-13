package open.vincentf13.sdk.infra.mysql.retry.task.repository;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("retry_task")
public class RetryTaskPO {

  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  private String bizType;
  private String bizKey;
  private RetryTaskStatus status;
  private Integer priority;
  private String payload;
  private String resultMsg;
  private Integer retryCount;
  private Integer maxRetries;
  private Instant nextRunTime;
  private Integer version;
  private Instant createdAt;
  private Instant updatedAt;
}
