package open.vincentf13.exchange.risk.infra.persistence.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiquidationTaskPO {

    private Long taskId;
    private Long positionId;
    private Long userId;
    private Long instrumentId;
    private String status;
    private Integer priority;
    private Instant queuedAt;
    private String reason;
    private Instant startedAt;
    private Instant processedAt;
    private BigDecimal liquidationPrice;
    private BigDecimal liquidationPnl;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetries;
    private Instant createdAt;
    private Instant updatedAt;
}
