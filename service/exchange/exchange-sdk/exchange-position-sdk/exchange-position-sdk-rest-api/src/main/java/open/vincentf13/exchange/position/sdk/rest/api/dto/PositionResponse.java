package open.vincentf13.exchange.position.sdk.rest.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;

public record PositionResponse(
    Long positionId,
    Long userId,
    Long instrumentId,
    PositionSide side,
    Integer leverage,
    BigDecimal margin,
    BigDecimal entryPrice,
    BigDecimal quantity,
    BigDecimal closingReservedQuantity,
    BigDecimal markPrice,
    BigDecimal marginRatio,
    BigDecimal unrealizedPnl,
    BigDecimal cumRealizedPnl,
    BigDecimal cumFee,
    BigDecimal cumFundingFee,
    BigDecimal liquidationPrice,
    PositionStatus status,
    Instant createdAt,
    Instant updatedAt,
    Instant closedAt) {}
