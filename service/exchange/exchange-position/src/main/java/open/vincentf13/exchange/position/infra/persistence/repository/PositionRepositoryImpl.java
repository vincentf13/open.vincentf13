package open.vincentf13.exchange.position.infra.persistence.repository;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.infra.persistence.mapper.PositionMapper;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PositionRepositoryImpl implements PositionRepository {

    private final PositionMapper mapper;

    @Override
    public Optional<Position> findActive(Long userId, Long instrumentId) {
        if (userId == null || instrumentId == null) {
            return Optional.empty();
        }
        PositionPO po = mapper.findActive(userId, instrumentId);
        return Optional.ofNullable(OpenMapstruct.map(po, Position.class));
    }

    @Override
    public boolean reserveForClose(Long userId, Long instrumentId, BigDecimal quantity, OrderSide orderSide) {
        if (userId == null || instrumentId == null || quantity == null || orderSide == null) {
            return false;
        }
        if (quantity.signum() <= 0) {
            return false;
        }
        return mapper.reserveForClose(userId, instrumentId, quantity, orderSide) > 0;
    }

}
