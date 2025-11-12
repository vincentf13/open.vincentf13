package open.vincentf13.exchange.position.sdk.rest.api.dto;

import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionResponse(
        Long positionId,
        Long userId,
        Long instrumentId,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal closingReservedQuantity,
        Instant updatedAt
) { }
