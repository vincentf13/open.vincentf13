package open.vincentf13.exchange.risk.margin.sdk.rest.api;

import java.math.BigDecimal;
import java.time.Instant;

public record RiskLimitResponse(
        Long instrumentId,
        BigDecimal initialMarginRate,
        Integer maxLeverage,
        BigDecimal maintenanceMarginRate,
        BigDecimal liquidationFeeRate,
        BigDecimal positionSizeMin,
        BigDecimal positionSizeMax,
        BigDecimal maxPositionValue,
        BigDecimal maxOrderValue,
        Boolean isActive,
        Instant updatedAt
) {
}
