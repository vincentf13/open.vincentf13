package open.vincentf13.exchange.position.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.infra.persistence.mapper.PositionMapper;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class PositionRepositoryImpl implements PositionRepository {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int DEFAULT_LEVERAGE = 1;

    private final PositionMapper mapper;
    private final DefaultIdGenerator idGenerator;

    @Override
    public Optional<Position> findActive(Long userId, Long instrumentId) {
        if (userId == null || instrumentId == null) {
            return Optional.empty();
        }
        PositionPO po = mapper.findActive(userId, instrumentId);
        return Optional.ofNullable(OpenMapstruct.map(po, Position.class));
    }

    @Override
    public List<Position> findByUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return OpenMapstruct.mapList(mapper.findByUser(userId), Position.class);
    }

    @Override
    public Position save(Position position) {
        if (position == null) {
            return null;
        }
        PositionPO po = OpenMapstruct.map(position, PositionPO.class);
        po.setExpectedVersion(position.getVersion());
        Instant now = Instant.now();
        if (po.getPositionId() == null) {
            po.setPositionId(idGenerator.newLong());
            po.setStatus(defaultStatus(po.getStatus()));
            po.setLeverage(defaultLeverage(po.getLeverage()));
            po.setMargin(defaultBigDecimal(po.getMargin()));
            po.setEntryPrice(defaultBigDecimal(po.getEntryPrice()));
            po.setQuantity(defaultBigDecimal(po.getQuantity()));
            po.setClosingReservedQuantity(defaultBigDecimal(po.getClosingReservedQuantity()));
            po.setMarkPrice(defaultBigDecimal(po.getMarkPrice()));
            po.setMarginRatio(defaultBigDecimal(po.getMarginRatio()));
            po.setUnrealizedPnl(defaultBigDecimal(po.getUnrealizedPnl()));
            po.setRealizedPnl(defaultBigDecimal(po.getRealizedPnl()));
            po.setLiquidationPrice(defaultBigDecimal(po.getLiquidationPrice()));
            po.setBankruptcyPrice(defaultBigDecimal(po.getBankruptcyPrice()));
            po.setVersion(po.getVersion() == null ? 0 : po.getVersion());
            po.setCreatedAt(now);
            po.setUpdatedAt(now);
            mapper.insertSelective(po);
        } else {
            po.setUpdatedAt(now);
            mapper.updateSelective(po);
        }
        return OpenMapstruct.map(po, Position.class);
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
    public void releaseReserve(Long userId, Long instrumentId, BigDecimal quantity) {
        if (userId == null || instrumentId == null || quantity == null) {
            return;
        }
        if (quantity.signum() <= 0) {
            return;
        }
        mapper.releaseReserve(userId, instrumentId, quantity);
    }

    @Override
    public Position upsertDemoPosition(Long userId, Long instrumentId, OrderSide side, BigDecimal quantity) {
        Instant now = Instant.now();
        PositionPO po = PositionPO.builder()
                .positionId(idGenerator.newLong())
                .userId(userId)
                .instrumentId(instrumentId)
                .side(side)
                .leverage(DEFAULT_LEVERAGE)
                .margin(defaultBigDecimal(null))
                .entryPrice(defaultBigDecimal(null))
                .quantity(defaultBigDecimal(quantity))
                .closingReservedQuantity(BigDecimal.ZERO)
                .markPrice(defaultBigDecimal(null))
                .marginRatio(defaultBigDecimal(null))
                .unrealizedPnl(defaultBigDecimal(null))
                .realizedPnl(defaultBigDecimal(null))
                .liquidationPrice(defaultBigDecimal(null))
                .bankruptcyPrice(defaultBigDecimal(null))
                .status(STATUS_ACTIVE)
                .lastTradeId(null)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .closedAt(null)
                .build();
        mapper.upsertDemo(po);
        return findActive(userId, instrumentId).orElseGet(() -> OpenMapstruct.map(po, Position.class));
    }

    private static String defaultStatus(String status) {
        return status == null ? STATUS_ACTIVE : status;
    }

    private static Integer defaultLeverage(Integer leverage) {
        return leverage == null ? DEFAULT_LEVERAGE : leverage;
    }

    private static BigDecimal defaultBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
