package open.vincentf13.exchange.marketdata.domain.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain entity describing the latest mark price derived from recent trades.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkPriceSnapshot {

    private Long snapshotId;
    @NotNull
    private Long instrumentId;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal markPrice;
    private Long tradeId;
    private Instant tradeExecutedAt;
    @NotNull
    private Instant calculatedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
