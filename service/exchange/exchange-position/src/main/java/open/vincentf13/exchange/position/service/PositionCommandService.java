package open.vincentf13.exchange.position.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.infra.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionCommandService {

    private final PositionRepository positionRepository;

    public PositionReserveResult reserveForClose(Long userId, Long instrumentId, BigDecimal quantity) {
        Optional<Position> positionOpt = positionRepository.findActive(userId, instrumentId);
        if (positionOpt.isEmpty()) {
            return PositionReserveResult.rejected("POSITION_NOT_FOUND");
        }
        Position position = positionOpt.get();
        if (quantity == null || quantity.signum() <= 0) {
            return PositionReserveResult.rejected("INVALID_QUANTITY");
        }
        if (position.availableToClose().compareTo(quantity) < 0) {
            return PositionReserveResult.rejected("INSUFFICIENT_POSITION");
        }
        boolean success = positionRepository.reserveForClose(userId, instrumentId, quantity);
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
