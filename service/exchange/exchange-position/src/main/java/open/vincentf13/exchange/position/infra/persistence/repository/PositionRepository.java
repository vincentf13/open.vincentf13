package open.vincentf13.exchange.position.infra.persistence.repository;

import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.position.domain.model.Position;

import java.math.BigDecimal;
import java.util.Optional;

public interface PositionRepository {

    Optional<Position> findActive(Long userId, Long instrumentId);

    Optional<Position> findById(Long positionId);

    boolean reserveForClose(Long userId, Long instrumentId, BigDecimal quantity, OrderSide orderSide);

    boolean updateLeverage(Long positionId, Integer leverage);
}
