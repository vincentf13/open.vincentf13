package open.vincentf13.exchange.risk.sdk.rest.api;

import java.math.BigDecimal;
import java.time.Instant;

public record RiskLimitResponse(
        Long instrumentId,
        BigDecimal initialMarginRate,
        Integer maxLeverage,
        BigDecimal maintenanceMarginRate,
        BigDecimal liquidationFeeRate,
        Boolean isActive,
        Instant updatedAt
) {
}
