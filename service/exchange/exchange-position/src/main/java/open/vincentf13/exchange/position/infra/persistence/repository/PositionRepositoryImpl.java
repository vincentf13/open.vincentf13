package open.vincentf13.exchange.position.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.infra.persistence.mapper.PositionMapper;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PositionRepositoryImpl implements PositionRepository {

    private final PositionMapper mapper;
    private final DefaultIdGenerator idGenerator;

    @Override
    public Optional<Position> findActive(Long userId, Long instrumentId) {
        if (userId == null || instrumentId == null) {
            return Optional.empty();
        }
        PositionPO condition = PositionPO.builder()
                .userId(userId)
                .instrumentId(instrumentId)
                .status("ACTIVE")
                .build();
        PositionPO po = mapper.findBy(condition);
        return Optional.ofNullable(OpenMapstruct.map(po, Position.class));
    }

    @Override
    public Optional<Position> findById(Long positionId) {
        if (positionId == null) {
            return Optional.empty();
        }
        PositionPO condition = PositionPO.builder()
                .positionId(positionId)
                .build();
        PositionPO po = mapper.findBy(condition);
        return Optional.ofNullable(OpenMapstruct.map(po, Position.class));
    }

    @Override
    public Position createDefault(Long userId, Long instrumentId) {
        if (userId == null || instrumentId == null) {
            return null;
        }
        PositionPO po = PositionPO.builder()
                .positionId(idGenerator.newLong())
                .userId(userId)
                .instrumentId(instrumentId)
                .leverage(1)
                .margin(BigDecimal.ZERO)
                .side(OrderSide.LONG)
                .entryPrice(BigDecimal.ZERO)
                .quantity(BigDecimal.ZERO)
                .closingReservedQuantity(BigDecimal.ZERO)
                .markPrice(BigDecimal.ZERO)
                .marginRatio(BigDecimal.ZERO)
                .unrealizedPnl(BigDecimal.ZERO)
                .realizedPnl(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();
        try {
            mapper.insertDefault(po);
            return OpenMapstruct.map(po, Position.class);
        } catch (DuplicateKeyException duplicateKeyException) {
            PositionPO existing = PositionPO.builder()
                    .userId(userId)
                    .instrumentId(instrumentId)
                    .status("ACTIVE")
                    .build();
            PositionPO poExisting = mapper.findBy(existing);
            return OpenMapstruct.map(poExisting, Position.class);
        }
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

    @Override
    public boolean updateLeverage(Long positionId, Integer leverage) {
        if (positionId == null || leverage == null) {
            return false;
        }
        return mapper.updateLeverage(positionId, leverage) > 0;
    }

}
