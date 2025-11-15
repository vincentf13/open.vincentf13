package open.vincentf13.exchange.position.sdk.rest.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionResponse(
        Long positionId,
        Long userId,
        Long instrumentId,
        PositionSide side,
        BigDecimal quantity,
        BigDecimal closingReservedQuantity,
        Instant updatedAt
) { }
