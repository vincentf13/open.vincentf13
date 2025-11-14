package open.vincentf13.exchange.position.infra.persistence.repository;

import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.position.domain.model.Position;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PositionRepository {

    Optional<Position> findActive(Long userId, Long instrumentId);

    List<Position> findByUser(Long userId);

    boolean reserveForClose(Long userId, Long instrumentId, BigDecimal quantity, OrderSide orderSide);
}
