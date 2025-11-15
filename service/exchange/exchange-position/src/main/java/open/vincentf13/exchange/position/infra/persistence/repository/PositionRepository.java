package open.vincentf13.exchange.position.infra.persistence.repository;

import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.model.PositionSide;

import java.math.BigDecimal;
import java.util.Optional;

public interface PositionRepository {

    Optional<Position> findActive(Long userId, Long instrumentId);

    Optional<Position> findById(Long positionId);

    Position createDefault(Long userId, Long instrumentId);

    boolean reserveForClose(Long userId, Long instrumentId, BigDecimal quantity, PositionSide side);

    boolean updateLeverage(Long positionId, Integer leverage);
}
