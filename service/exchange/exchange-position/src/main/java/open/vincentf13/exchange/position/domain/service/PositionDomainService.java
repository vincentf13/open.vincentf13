package open.vincentf13.exchange.position.domain.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.model.PositionEvent;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.cache.MarkPriceCache;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionEventRepository;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionEventType;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
public class PositionDomainService {

    private final PositionRepository positionRepository;
    private final PositionEventRepository positionEventRepository;
    private final MarkPriceCache markPriceCache;

    private static final BigDecimal MAINTENANCE_MARGIN_RATE_DEFAULT = BigDecimal.valueOf(0.005);

    public PositionSide toPositionSide(OrderSide orderSide) {
        if (orderSide == null) {
            return null;
        }
        return orderSide == OrderSide.BUY ? PositionSide.LONG : PositionSide.SHORT;
    }

    public boolean shouldSplitTrade(Position position, PositionSide targetSide, BigDecimal quantity) {
        if (position == null) {
            return false;
        }
        return position.getSide() != targetSide && position.getQuantity().compareTo(quantity) < 0;
    }

    public TradeSplit calculateTradeSplit(Position position, BigDecimal quantity) {
        BigDecimal closeQty = position.getQuantity();
        BigDecimal flipQty = quantity.subtract(closeQty);
        return new TradeSplit(closeQty, flipQty);
    }

    public ReserveForCloseResult calculateReserveForClose(Position position,
                                                          BigDecimal quantity) {
        if (position == null) {
            return ReserveForCloseResult.rejected("POSITION_NOT_FOUND");
        }
        if (position.availableToClose().compareTo(quantity) < 0) {
            return ReserveForCloseResult.rejected("INSUFFICIENT_AVAILABLE");
        }
        
        BigDecimal newReservedQuantity = position.getClosingReservedQuantity().add(quantity);
        BigDecimal avgOpenPrice = position.getEntryPrice();
        
        return ReserveForCloseResult.accepted(newReservedQuantity, avgOpenPrice);
    }
    
    public Collection<Position> processTradeForUser(Long userId,
                                                     Long instrumentId,
                                                     OrderSide orderSide,
                                                     BigDecimal price,
                                                     BigDecimal quantity,
                                                     Long tradeId,
                                                     Instant executedAt) {
        PositionSide side = toPositionSide(orderSide);

        Position position = positionRepository.findOne(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getUserId, userId)
                        .eq(PositionPO::getInstrumentId, instrumentId)
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
                .orElse(null);

        if (shouldSplitTrade(position, side, quantity)) {
            TradeSplit split = calculateTradeSplit(position, quantity);

            List<Position> results = new ArrayList<>();
            results.addAll(processTradeForUser(userId, instrumentId, orderSide, price, split.closeQuantity(), tradeId, executedAt));
            results.addAll(processTradeForUser(userId, instrumentId, orderSide, price, split.flipQuantity(), tradeId, executedAt));
            return results;
        }

        if (position == null) {
            position = Position.createDefault(userId, instrumentId, side);
        }

        TradeExecutionData execData = processTradeExecution(position, side, price, quantity, executedAt);
        Position updatedPosition = execData.updatedPosition();

        if (position.getPositionId() == null) {
            positionRepository.insertSelective(updatedPosition);
        } else {
            updatedPosition.setVersion(updatedPosition.getVersion() + 1);
            boolean updated = positionRepository.updateSelectiveBy(
                    updatedPosition,
                    Wrappers.<PositionPO>lambdaUpdate()
                            .eq(PositionPO::getPositionId, updatedPosition.getPositionId())
                            .eq(PositionPO::getVersion, updatedPosition.getVersion()));
            if (!updated) {
                throw OpenException.of(PositionErrorCode.POSITION_CONCURRENT_UPDATE,
                        Map.of("positionId", updatedPosition.getPositionId()));
            }
        }

        PositionEvent event = PositionEvent.createTradeEvent(
                updatedPosition.getPositionId(),
                userId,
                instrumentId,
                execData.eventType(),
                execData.deltaQuantity(),
                updatedPosition.getQuantity(),
                updatedPosition.getClosingReservedQuantity(),
                updatedPosition.getEntryPrice(),
                updatedPosition.getUnrealizedPnl(),
                tradeId,
                executedAt
        );
        positionEventRepository.insert(event);

        return Collections.singletonList(updatedPosition);
    }

    private TradeExecutionData processTradeExecution(Position position,
                                                     PositionSide side,
                                                     BigDecimal price,
                                                     BigDecimal quantity,
                                                     Instant executedAt) {

        Position updatedPosition = OpenObjectMapper.convert(position, Position.class);

        markPriceCache.get(updatedPosition.getInstrumentId())
                      .ifPresent(updatedPosition::setMarkPrice);

        PositionEventType eventType;
        boolean isIncrease;

        if (updatedPosition.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            eventType = PositionEventType.POSITION_OPENED;
            updatedPosition.setSide(side);
            isIncrease = true;
        } else if (updatedPosition.getSide() == side) {
            eventType = PositionEventType.POSITION_INCREASED;
            isIncrease = true;
        } else {
            eventType = PositionEventType.POSITION_DECREASED;
            isIncrease = false;
        }

        // 增持
        if (isIncrease) {
            updatedPosition.setEntryPrice(
                    updatedPosition.getEntryPrice().multiply(updatedPosition.getQuantity())
                            .add(price.multiply(quantity))
                            .divide(updatedPosition.getQuantity().add(quantity), 12, RoundingMode.HALF_UP)
            );
            updatedPosition.setQuantity(updatedPosition.getQuantity().add(quantity));
        } else {
            updatedPosition.setQuantity(updatedPosition.getQuantity().subtract(quantity.min(updatedPosition.getQuantity())));
            updatedPosition.setClosingReservedQuantity(updatedPosition.getClosingReservedQuantity().subtract(quantity.min(updatedPosition.getQuantity())).max(BigDecimal.ZERO));

            if (updatedPosition.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                updatedPosition.setStatus(PositionStatus.CLOSED);
                updatedPosition.setClosedAt(executedAt);
                eventType = PositionEventType.POSITION_CLOSED;
            }
        }

        updatedPosition.setMargin(updatedPosition.getEntryPrice().multiply(updatedPosition.getQuantity()).abs()
                .divide(BigDecimal.valueOf(updatedPosition.getLeverage()), 12, RoundingMode.HALF_UP));

        if (updatedPosition.getSide() == PositionSide.LONG) {
            updatedPosition.setUnrealizedPnl(updatedPosition.getMarkPrice().subtract(updatedPosition.getEntryPrice()).multiply(updatedPosition.getQuantity()));
        } else {
            updatedPosition.setUnrealizedPnl(updatedPosition.getEntryPrice().subtract(updatedPosition.getMarkPrice()).multiply(updatedPosition.getQuantity()));
        }

        if (updatedPosition.getMarkPrice().multiply(updatedPosition.getQuantity()).abs().compareTo(BigDecimal.ZERO) == 0) {
            updatedPosition.setMarginRatio(BigDecimal.ZERO);
        } else {
            updatedPosition.setMarginRatio(updatedPosition.getMargin().add(updatedPosition.getUnrealizedPnl())
                    .divide(updatedPosition.getMarkPrice().multiply(updatedPosition.getQuantity()).abs(), 4, RoundingMode.HALF_UP));
        }

        if (updatedPosition.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            if (updatedPosition.getSide() == PositionSide.LONG) {
                updatedPosition.setLiquidationPrice(
                        updatedPosition.getEntryPrice()
                                .subtract(updatedPosition.getMargin().divide(updatedPosition.getQuantity(), 12, RoundingMode.HALF_UP))
                                .divide(BigDecimal.ONE.subtract(MAINTENANCE_MARGIN_RATE_DEFAULT), 12, RoundingMode.HALF_UP)
                );
            } else {
                updatedPosition.setLiquidationPrice(
                        updatedPosition.getEntryPrice()
                                .add(updatedPosition.getMargin().divide(updatedPosition.getQuantity(), 12, RoundingMode.HALF_UP))
                                .divide(BigDecimal.ONE.add(MAINTENANCE_MARGIN_RATE_DEFAULT), 12, RoundingMode.HALF_UP)
                );
            }
        } else {
            updatedPosition.setLiquidationPrice(BigDecimal.ZERO);
        }

        return new TradeExecutionData(eventType, isIncrease ? quantity : quantity.negate(), updatedPosition);
    }

    public record ReserveForCloseResult(boolean success, BigDecimal newReservedQuantity, BigDecimal avgOpenPrice,
                                        String reason) {
        public static ReserveForCloseResult accepted(BigDecimal newReservedQuantity,
                                                     BigDecimal avgOpenPrice) {
            return new ReserveForCloseResult(true, newReservedQuantity, avgOpenPrice, null);
        }
        
        public static ReserveForCloseResult rejected(String reason) {
            return new ReserveForCloseResult(false, null, null, reason);
        }
    }

    public record TradeSplit(BigDecimal closeQuantity, BigDecimal flipQuantity) {
    }

    private record TradeExecutionData(PositionEventType eventType, BigDecimal deltaQuantity,
                                      Position updatedPosition) {
    }
}
