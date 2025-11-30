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
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

        BigDecimal cachedMarkPrice = markPriceCache.get(instrumentId).orElse(null);

        TradeExecutionData execData = processTradeExecution(position, orderSide, price, quantity, cachedMarkPrice, executedAt);

        if (position.getPositionId() == null) {
            positionRepository.insertSelective(position);
        } else {
            position.setVersion(position.getVersion() + 1);
            boolean updated = positionRepository.updateSelectiveBy(
                    position,
                    Wrappers.<PositionPO>lambdaUpdate()
                            .eq(PositionPO::getPositionId, position.getPositionId())
                            .eq(PositionPO::getVersion, position.getVersion()));
            if (!updated) {
                throw OpenException.of(PositionErrorCode.POSITION_CONCURRENT_UPDATE,
                        Map.of("positionId", position.getPositionId()));
            }
        }

        PositionEvent event = PositionEvent.createTradeEvent(
                position.getPositionId(),
                userId,
                instrumentId,
                execData.eventType(),
                execData.deltaQuantity(),
                execData.deltaPnl(),
                position.getQuantity(),
                position.getClosingReservedQuantity(),
                position.getEntryPrice(),
                position.getUnrealizedPnl(),
                tradeId,
                executedAt
        );
        positionEventRepository.insert(event);

        return Collections.singletonList(position);
    }

    private TradeExecutionData processTradeExecution(Position position,
                                                     OrderSide orderSide,
                                                     BigDecimal price,
                                                     BigDecimal quantity,
                                                     BigDecimal cachedMarkPrice,
                                                     Instant executedAt) {
        PositionSide side = toPositionSide(orderSide);
        
        if (cachedMarkPrice != null) {
            position.setMarkPrice(cachedMarkPrice);
        }
        BigDecimal markPrice = position.getMarkPrice();
        
        BigDecimal deltaQuantity = quantity;
        BigDecimal deltaPnl = BigDecimal.ZERO;
        PositionEventType eventType;
        boolean isIncrease;
        
        if (position.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            eventType = PositionEventType.POSITION_OPENED;
            position.setSide(side);
            isIncrease = true;
        } else if (position.getSide() == side) {
            eventType = PositionEventType.POSITION_INCREASED;
            isIncrease = true;
        } else {
            eventType = PositionEventType.POSITION_DECREASED;
            isIncrease = false;
        }
        
        if (isIncrease) {
            BigDecimal totalCost = position.getEntryPrice().multiply(position.getQuantity()).add(price.multiply(quantity));
            BigDecimal newQuantity = position.getQuantity().add(quantity);
            position.setQuantity(newQuantity);
            position.setEntryPrice(totalCost.divide(newQuantity, 12, RoundingMode.HALF_UP));
        } else {
            BigDecimal closeQty = quantity.min(position.getQuantity());
            
            BigDecimal pnl;
            if (position.getSide() == PositionSide.LONG) {
                pnl = price.subtract(position.getEntryPrice()).multiply(closeQty);
            } else {
                pnl = position.getEntryPrice().subtract(price).multiply(closeQty);
            }
            deltaPnl = pnl;
            position.setRealizedPnl(position.getRealizedPnl().add(pnl));
            
            position.setQuantity(position.getQuantity().subtract(closeQty));
            position.setClosingReservedQuantity(position.getClosingReservedQuantity().subtract(closeQty).max(BigDecimal.ZERO));
            
            if (position.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                position.setStatus(PositionStatus.CLOSED);
                position.setClosedAt(executedAt);
                eventType = PositionEventType.POSITION_CLOSED;
            }
        }
        
        BigDecimal margin = position.getEntryPrice().multiply(position.getQuantity()).abs().divide(BigDecimal.valueOf(position.getLeverage()), 12, RoundingMode.HALF_UP);
        position.setMargin(margin);
        
        BigDecimal unrealizedPnl;
        if (position.getSide() == PositionSide.LONG) {
            unrealizedPnl = markPrice.subtract(position.getEntryPrice()).multiply(position.getQuantity());
        } else {
            unrealizedPnl = position.getEntryPrice().subtract(markPrice).multiply(position.getQuantity());
        }
        position.setUnrealizedPnl(unrealizedPnl);
        
        BigDecimal notional = markPrice.multiply(position.getQuantity()).abs();
        if (notional.compareTo(BigDecimal.ZERO) == 0) {
            position.setMarginRatio(BigDecimal.ZERO);
        } else {
            position.setMarginRatio(margin.add(unrealizedPnl).divide(notional, 4, RoundingMode.HALF_UP));
        }
        
        BigDecimal mmr = MAINTENANCE_MARGIN_RATE_DEFAULT;
        if (position.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal marginPerUnit = margin.divide(position.getQuantity(), 12, RoundingMode.HALF_UP);
            if (position.getSide() == PositionSide.LONG) {
                position.setLiquidationPrice(position.getEntryPrice().subtract(marginPerUnit).divide(BigDecimal.ONE.subtract(mmr), 12, RoundingMode.HALF_UP));
            } else {
                position.setLiquidationPrice(position.getEntryPrice().add(marginPerUnit).divide(BigDecimal.ONE.add(mmr), 12, RoundingMode.HALF_UP));
            }
        } else {
            position.setLiquidationPrice(BigDecimal.ZERO);
        }

        return new TradeExecutionData(eventType, isIncrease ? deltaQuantity : deltaQuantity.negate(), deltaPnl);
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

    private record TradeExecutionData(PositionEventType eventType, BigDecimal deltaQuantity, BigDecimal deltaPnl) {
    }
}
