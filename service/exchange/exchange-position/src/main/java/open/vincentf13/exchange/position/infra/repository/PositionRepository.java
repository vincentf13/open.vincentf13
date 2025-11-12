package open.vincentf13.exchange.position.infra.repository;

import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.position.domain.model.Position;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PositionRepository {

    Optional<Position> findActive(Long userId, Long instrumentId);

    List<Position> findByUser(Long userId);

    Position save(Position position);

    boolean reserveForClose(Long userId, Long instrumentId, BigDecimal quantity);

    void releaseReserve(Long userId, Long instrumentId, BigDecimal quantity);

    Position upsertDemoPosition(Long userId, Long instrumentId, OrderSide side, BigDecimal quantity);
}
