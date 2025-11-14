package open.vincentf13.exchange.position.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.position.infra.repository.PositionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionCommandService {

    private final PositionRepository positionRepository;

    public PositionReserveResult reserveForClose(
            Long userId,
            Long instrumentId,
            BigDecimal quantity,
            OrderSide orderSide
    ) {
        if (userId == null || instrumentId == null) {
            return PositionReserveResult.rejected("POSITION_NOT_FOUND");
        }
        if (quantity == null || quantity.signum() <= 0) {
            return PositionReserveResult.rejected("INVALID_QUANTITY");
        }
        if (orderSide == null) {
            return PositionReserveResult.rejected("ORDER_SIDE_REQUIRED");
        }
        boolean success = positionRepository.reserveForClose(userId, instrumentId, quantity, orderSide);
        if (!success) {
            return PositionReserveResult.rejected("RESERVE_FAILED");
        }
        return PositionReserveResult.accepted(quantity);
    }

    public record PositionReserveResult(boolean success, BigDecimal reservedQuantity, String reason, Instant processedAt) {
        public static PositionReserveResult accepted(BigDecimal quantity) {
            return new PositionReserveResult(true, quantity, null, Instant.now());
        }

        public static PositionReserveResult rejected(String reason) {
            return new PositionReserveResult(false, BigDecimal.ZERO, reason, Instant.now());
        }

        public boolean isCloseIntent() {
            return true;
        }
    }
}
