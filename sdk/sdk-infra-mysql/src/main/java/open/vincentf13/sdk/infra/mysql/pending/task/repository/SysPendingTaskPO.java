package open.vincentf13.sdk.infra.mysql.pending.task.repository;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysPendingTaskPO {
    
    private Long id;
    private String bizType;
    private String bizKey;
    private SysPendingTaskStatus status;
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
