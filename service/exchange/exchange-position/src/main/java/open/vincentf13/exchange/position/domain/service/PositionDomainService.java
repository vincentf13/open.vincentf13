package open.vincentf13.exchange.position.domain.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.model.PositionEvent;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.cache.InstrumentCache;
import open.vincentf13.exchange.position.infra.cache.MarkPriceCache;
import open.vincentf13.exchange.position.infra.cache.RiskLimitCache;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionEventRepository;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionEventType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private final RiskLimitCache riskLimitCache;
    private final InstrumentCache instrumentCache;

    private static final BigDecimal MAINTENANCE_MARGIN_RATE_DEFAULT = BigDecimal.valueOf(0.005);
    private static final BigDecimal CONTRACT_MULTIPLIER = BigDecimal.ONE;

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

    public Collection<Position> processTradeForUser(@NotNull Long userId,
                                                     @NotNull Long instrumentId,
                                                     @NotNull OrderSide orderSide,
                                                     @NotNull @DecimalMin(value = ValidationConstant.Names.PRICE_MIN, inclusive = false) BigDecimal price,
                                                     @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = false) BigDecimal quantity,
                                                     @NotNull Long tradeId,
                                                     @NotNull Instant executedAt) {
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
            int expectedVersion = position.safeVersion();
            updatedPosition.setVersion(expectedVersion + 1);
            boolean updated = positionRepository.updateSelectiveBy(
                    updatedPosition,
                    Wrappers.<PositionPO>lambdaUpdate()
                            .eq(PositionPO::getPositionId, updatedPosition.getPositionId())
                            .eq(PositionPO::getVersion, expectedVersion));
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
                execData.deltaMargin(),
                execData.realizedPnl(),
                execData.tradeFee(),
                execData.fundingFee(),
                updatedPosition.getQuantity(),
                updatedPosition.getClosingReservedQuantity(),
                updatedPosition.getEntryPrice(),
                updatedPosition.getLeverage(),
                updatedPosition.getMargin(),
                updatedPosition.getUnrealizedPnl(),
                updatedPosition.getLiquidationPrice(),
                tradeId,
                executedAt
        );
        positionEventRepository.insert(event);

        return Collections.singletonList(updatedPosition);
    }

    public Collection<Position> openPosition(@NotNull Long userId,
                                             @NotNull Long instrumentId,
                                             @NotNull OrderSide orderSide,
                                             @NotNull @DecimalMin(value = ValidationConstant.Names.PRICE_MIN, inclusive = false) BigDecimal price,
                                             @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = false) BigDecimal quantity,
                                             @NotNull BigDecimal marginUsed,
                                             @NotNull BigDecimal feeCharged,
                                             @NotNull BigDecimal feeRefund,
                                             @NotNull Long referenceId,
                                             @NotNull Instant executedAt) {
        if (positionEventRepository.existsByReference(PositionReferenceType.TRADE, referenceId)) {
            return positionRepository.findOne(
                            Wrappers.lambdaQuery(PositionPO.class)
                                    .eq(PositionPO::getUserId, userId)
                                    .eq(PositionPO::getInstrumentId, instrumentId)
                                    .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
                    .map(Collections::singletonList)
                    .orElse(Collections.emptyList());
        }
        
        PositionSide side = toPositionSide(orderSide);
        Position position = positionRepository.findOne(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getUserId, userId)
                        .eq(PositionPO::getInstrumentId, instrumentId)
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
                .orElse(Position.createDefault(userId, instrumentId, side));

        // 開倉 因併發訂單，變為平倉，需釋放保證金，並處理flip的情況
        if (position.getSide() != side) {
            if (shouldSplitTrade(position, side, quantity)) {
                TradeSplit split = calculateTradeSplit(position, quantity);

                BigDecimal flipRatio = split.flipQuantity().divide(quantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
                BigDecimal flipMargin = marginUsed.multiply(flipRatio);
                BigDecimal closeMargin = marginUsed.subtract(flipMargin);

                BigDecimal flipFeeCharged = feeCharged.multiply(flipRatio);
                BigDecimal flipFeeRefund = feeRefund.multiply(flipRatio);
                BigDecimal closeFeeCharged = feeCharged.subtract(flipFeeCharged);
                BigDecimal closeFeeRefund = feeRefund.subtract(flipFeeRefund);

                List<Position> results = new ArrayList<>();
                results.addAll(openPosition(userId, instrumentId, orderSide, price, split.closeQuantity(), closeMargin, closeFeeCharged, closeFeeRefund, referenceId, executedAt));
                results.addAll(openPosition(userId, instrumentId, orderSide, price, split.flipQuantity(), flipMargin, flipFeeCharged, flipFeeRefund, referenceId, executedAt));
                return results;
            }

            PositionCloseResult result = closePosition(position, userId, instrumentId, price, quantity, feeCharged, feeRefund, referenceId, executedAt, false);
            return Collections.singletonList(result.position());
        } else {
            Position updatedPosition = OpenObjectMapper.convert(position, Position.class);
            
            markPriceCache.get(instrumentId)
                          .ifPresent(updatedPosition::setMarkPrice);
            
            BigDecimal contractMultiplier = instrumentCache.get(instrumentId)
                                                           .map(instrument -> instrument.contractSize() != null ? instrument.contractSize() : CONTRACT_MULTIPLIER)
                                                           .orElse(CONTRACT_MULTIPLIER);
            
            BigDecimal maintenanceMarginRate = riskLimitCache.get(instrumentId)
                                                             .map(riskLimit -> riskLimit.maintenanceMarginRate() != null ? riskLimit.maintenanceMarginRate() : MAINTENANCE_MARGIN_RATE_DEFAULT)
                                                             .orElse(MAINTENANCE_MARGIN_RATE_DEFAULT);
            
            BigDecimal existingQuantity = safe(position.getQuantity());
            BigDecimal existingEntryPrice = safe(position.getEntryPrice());
            BigDecimal existingMargin = safe(position.getMargin());
            BigDecimal existingCumFee = safe(position.getCumFee());
            
            BigDecimal newQuantity = existingQuantity.add(quantity);
            BigDecimal newEntryPrice =  existingEntryPrice.multiply(existingQuantity)
                                                           .add(price.multiply(quantity))
                                                           .divide(newQuantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
            
            updatedPosition.setQuantity(newQuantity);
            updatedPosition.setEntryPrice(newEntryPrice);
            updatedPosition.setClosingReservedQuantity(safe(position.getClosingReservedQuantity()));
            updatedPosition.setMargin(existingMargin.add(marginUsed));
            BigDecimal feeDelta = feeCharged.subtract(feeRefund);
            updatedPosition.setCumFee(existingCumFee.add(feeDelta));
            
            BigDecimal effectiveMarkPrice = updatedPosition.getMarkPrice();
            if (effectiveMarkPrice == null || effectiveMarkPrice.compareTo(BigDecimal.ZERO) == 0) {
                effectiveMarkPrice = price;
                updatedPosition.setMarkPrice(price);
            }
            
            if (updatedPosition.getSide() == PositionSide.LONG) {
                updatedPosition.setUnrealizedPnl(effectiveMarkPrice.subtract(updatedPosition.getEntryPrice())
                                                                   .multiply(updatedPosition.getQuantity())
                                                                   .multiply(contractMultiplier));
            } else {
                updatedPosition.setUnrealizedPnl(updatedPosition.getEntryPrice().subtract(effectiveMarkPrice)
                                                                .multiply(updatedPosition.getQuantity())
                                                                .multiply(contractMultiplier));
            }
            
            BigDecimal notional = effectiveMarkPrice.multiply(updatedPosition.getQuantity()).multiply(contractMultiplier).abs();
            if (notional.compareTo(BigDecimal.ZERO) == 0) {
                updatedPosition.setMarginRatio(BigDecimal.ZERO);
            } else {
                updatedPosition.setMarginRatio(updatedPosition.getMargin().add(updatedPosition.getUnrealizedPnl())
                                                              .divide(notional, ValidationConstant.Names.MARGIN_RATIO_SCALE, RoundingMode.HALF_UP));
            }
            
            BigDecimal quantityTimesMultiplier = updatedPosition.getQuantity().multiply(contractMultiplier);
            if (quantityTimesMultiplier.compareTo(BigDecimal.ZERO) > 0) {
                if (updatedPosition.getSide() == PositionSide.LONG) {
                    updatedPosition.setLiquidationPrice(
                            updatedPosition.getEntryPrice()
                                           .subtract(updatedPosition.getMargin().divide(quantityTimesMultiplier, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP))
                                           .divide(BigDecimal.ONE.subtract(maintenanceMarginRate), ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP)
                                                       );
                } else {
                    updatedPosition.setLiquidationPrice(
                            updatedPosition.getEntryPrice()
                                           .add(updatedPosition.getMargin().divide(quantityTimesMultiplier, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP))
                                           .divide(BigDecimal.ONE.add(maintenanceMarginRate), ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP)
                                                       );
                }
            } else {
                updatedPosition.setLiquidationPrice(BigDecimal.ZERO);
            }
            
            if (position.getPositionId() == null || position.getStatus() != PositionStatus.ACTIVE) {
                positionRepository.insertSelective(updatedPosition);
            } else {
                int expectedVersion = position.safeVersion();
                updatedPosition.setVersion(expectedVersion + 1);
                boolean updated = positionRepository.updateSelectiveBy(
                        updatedPosition,
                        Wrappers.<PositionPO>lambdaUpdate()
                                .eq(PositionPO::getPositionId, updatedPosition.getPositionId())
                                .eq(PositionPO::getVersion, expectedVersion));
                if (!updated) {
                    throw OpenException.of(PositionErrorCode.POSITION_CONCURRENT_UPDATE,
                                           Map.of("positionId", updatedPosition.getPositionId()));
                }
            }
            
            PositionEvent event = PositionEvent.createTradeEvent(
                    updatedPosition.getPositionId(),
                    userId,
                    instrumentId,
                    existingQuantity.compareTo(BigDecimal.ZERO) == 0 ? PositionEventType.POSITION_OPENED : PositionEventType.POSITION_INCREASED,
                    quantity,
                    marginUsed,
                    BigDecimal.ZERO,
                    feeDelta,
                    BigDecimal.ZERO,
                    updatedPosition.getQuantity(),
                    updatedPosition.getClosingReservedQuantity(),
                    updatedPosition.getEntryPrice(),
                    updatedPosition.getLeverage(),
                    updatedPosition.getMargin(),
                    updatedPosition.getUnrealizedPnl(),
                    updatedPosition.getLiquidationPrice(),
                    referenceId,
                    executedAt
                                                                );
            positionEventRepository.insert(event);
            
            return Collections.singletonList(updatedPosition);
        }
    }

    public PositionCloseResult closePosition(Position position, Long userId, Long instrumentId,
                                             BigDecimal price, BigDecimal quantity,
                                             BigDecimal feeCharged, BigDecimal feeRefund,
                                             Long referenceId, Instant executedAt,
                                             boolean reduceReserved) {
        Position updatedPosition = OpenObjectMapper.convert(position, Position.class);

        markPriceCache.get(instrumentId)
                .ifPresent(updatedPosition::setMarkPrice);

        BigDecimal contractMultiplier = instrumentCache.get(instrumentId)
                .map(instrument -> instrument.contractSize() != null ? instrument.contractSize() : CONTRACT_MULTIPLIER)
                .orElse(CONTRACT_MULTIPLIER);

        BigDecimal maintenanceMarginRate = riskLimitCache.get(instrumentId)
                .map(riskLimit -> riskLimit.maintenanceMarginRate() != null ? riskLimit.maintenanceMarginRate() : MAINTENANCE_MARGIN_RATE_DEFAULT)
                .orElse(MAINTENANCE_MARGIN_RATE_DEFAULT);

        BigDecimal existingQuantity = safe(position.getQuantity());
        BigDecimal existingMargin = safe(position.getMargin());
        BigDecimal existingCumFee = safe(position.getCumFee());
        BigDecimal existingCumRealized = safe(position.getCumRealizedPnl());

        BigDecimal marginToRelease = existingMargin.multiply(quantity)
                .divide(existingQuantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);

        BigDecimal pnl;
        if (position.getSide() == PositionSide.LONG) {
            pnl = price.subtract(updatedPosition.getEntryPrice())
                    .multiply(quantity)
                    .multiply(contractMultiplier);
        } else {
            pnl = updatedPosition.getEntryPrice().subtract(price)
                    .multiply(quantity)
                    .multiply(contractMultiplier);
        }

        BigDecimal newQuantity = existingQuantity.subtract(quantity);
        updatedPosition.setQuantity(newQuantity);
        updatedPosition.setMargin(existingMargin.subtract(marginToRelease));
        updatedPosition.setCumRealizedPnl(existingCumRealized.add(pnl));

        if (reduceReserved) {
            BigDecimal existingReserved = safe(position.getClosingReservedQuantity());
            updatedPosition.setClosingReservedQuantity(existingReserved.subtract(quantity).max(BigDecimal.ZERO));
        }

        BigDecimal feeDelta = feeCharged.subtract(feeRefund);
        updatedPosition.setCumFee(existingCumFee.add(feeDelta));

        if (newQuantity.compareTo(BigDecimal.ZERO) == 0) {
            updatedPosition.setStatus(PositionStatus.CLOSED);
            updatedPosition.setClosedAt(executedAt);
        }

        BigDecimal effectiveMarkPrice = updatedPosition.getMarkPrice();
        if (effectiveMarkPrice == null || effectiveMarkPrice.compareTo(BigDecimal.ZERO) == 0) {
            effectiveMarkPrice = price;
            updatedPosition.setMarkPrice(price);
        }

        if (newQuantity.compareTo(BigDecimal.ZERO) > 0) {
            if (updatedPosition.getSide() == PositionSide.LONG) {
                updatedPosition.setUnrealizedPnl(effectiveMarkPrice.subtract(updatedPosition.getEntryPrice())
                        .multiply(updatedPosition.getQuantity())
                        .multiply(contractMultiplier));
            } else {
                updatedPosition.setUnrealizedPnl(updatedPosition.getEntryPrice().subtract(effectiveMarkPrice)
                        .multiply(updatedPosition.getQuantity())
                        .multiply(contractMultiplier));
            }
        } else {
            updatedPosition.setUnrealizedPnl(BigDecimal.ZERO);
        }

        if (updatedPosition.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal notional = effectiveMarkPrice.multiply(updatedPosition.getQuantity()).multiply(contractMultiplier).abs();
            if (notional.compareTo(BigDecimal.ZERO) == 0) {
                updatedPosition.setMarginRatio(BigDecimal.ZERO);
            } else {
                updatedPosition.setMarginRatio(updatedPosition.getMargin().add(updatedPosition.getUnrealizedPnl())
                        .divide(notional, ValidationConstant.Names.MARGIN_RATIO_SCALE, RoundingMode.HALF_UP));
            }

            BigDecimal quantityTimesMultiplier = updatedPosition.getQuantity().multiply(contractMultiplier);
            if (updatedPosition.getSide() == PositionSide.LONG) {
                updatedPosition.setLiquidationPrice(
                        updatedPosition.getEntryPrice()
                                .subtract(updatedPosition.getMargin().divide(quantityTimesMultiplier, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP))
                                .divide(BigDecimal.ONE.subtract(maintenanceMarginRate), ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP)
                );
            } else {
                updatedPosition.setLiquidationPrice(
                        updatedPosition.getEntryPrice()
                                .add(updatedPosition.getMargin().divide(quantityTimesMultiplier, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP))
                                .divide(BigDecimal.ONE.add(maintenanceMarginRate), ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP)
                );
            }
        } else {
            updatedPosition.setMarginRatio(BigDecimal.ZERO);
            updatedPosition.setLiquidationPrice(BigDecimal.ZERO);
        }

        if (position.getPositionId() == null || position.getStatus() != PositionStatus.ACTIVE) {
            positionRepository.insertSelective(updatedPosition);
        } else {
            int expectedVersion = position.safeVersion();
            updatedPosition.setVersion(expectedVersion + 1);
            boolean updated = positionRepository.updateSelectiveBy(
                    updatedPosition,
                    Wrappers.<PositionPO>lambdaUpdate()
                            .eq(PositionPO::getPositionId, updatedPosition.getPositionId())
                            .eq(PositionPO::getVersion, expectedVersion));
            if (!updated) {
                throw OpenException.of(PositionErrorCode.POSITION_CONCURRENT_UPDATE,
                        Map.of("positionId", updatedPosition.getPositionId()));
            }
        }

        PositionEvent event = PositionEvent.createTradeEvent(
                updatedPosition.getPositionId(),
                userId,
                instrumentId,
                newQuantity.compareTo(BigDecimal.ZERO) == 0 ? PositionEventType.POSITION_CLOSED : PositionEventType.POSITION_DECREASED,
                quantity.negate(),
                marginToRelease.negate(),
                pnl,
                feeDelta,
                BigDecimal.ZERO,
                updatedPosition.getQuantity(),
                updatedPosition.getClosingReservedQuantity(),
                updatedPosition.getEntryPrice(),
                updatedPosition.getLeverage(),
                updatedPosition.getMargin(),
                updatedPosition.getUnrealizedPnl(),
                updatedPosition.getLiquidationPrice(),
                referenceId,
                executedAt
        );
        positionEventRepository.insert(event);

        return new PositionCloseResult(updatedPosition, pnl, marginToRelease);
    }

    private TradeExecutionData processTradeExecution(@NotNull Position position,
                                                     @NotNull PositionSide side,
                                                     @NotNull @DecimalMin(value = ValidationConstant.Names.PRICE_MIN, inclusive = false) BigDecimal price,
                                                     @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = false) BigDecimal quantity,
                                                     @NotNull Instant executedAt) {

        Position updatedPosition = OpenObjectMapper.convert(position, Position.class);

        markPriceCache.get(position.getInstrumentId())
                      .ifPresent(updatedPosition::setMarkPrice);

        BigDecimal contractMultiplier = instrumentCache.get(position.getInstrumentId())
                .map(instrument -> instrument.contractSize() != null ? instrument.contractSize() : CONTRACT_MULTIPLIER)
                .orElse(CONTRACT_MULTIPLIER);

        BigDecimal maintenanceMarginRate = riskLimitCache.get(position.getInstrumentId())
                .map(riskLimit -> riskLimit.maintenanceMarginRate() != null ? riskLimit.maintenanceMarginRate() : MAINTENANCE_MARGIN_RATE_DEFAULT)
                .orElse(MAINTENANCE_MARGIN_RATE_DEFAULT);

        PositionEventType eventType;
        boolean isIncrease;

        BigDecimal existingQuantity = safe(position.getQuantity());
        BigDecimal existingMargin = safe(position.getMargin());
        BigDecimal existingReserved = safe(position.getClosingReservedQuantity());
        BigDecimal existingEntryPrice = safe(position.getEntryPrice());
        BigDecimal existingCumRealized = safe(position.getCumRealizedPnl());
        BigDecimal existingCumFee = safe(position.getCumFee());
        BigDecimal existingCumFunding = safe(position.getCumFundingFee());

        if (existingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            eventType = PositionEventType.POSITION_OPENED;
            updatedPosition.setSide(side);
            isIncrease = true;
        } else if (position.getSide() == side) {
            eventType = PositionEventType.POSITION_INCREASED;
            isIncrease = true;
        } else {
            eventType = PositionEventType.POSITION_DECREASED;
            isIncrease = false;
        }

        BigDecimal realizedPnl = BigDecimal.ZERO;
        BigDecimal deltaQuantity;

        if (isIncrease) {
            BigDecimal newQuantity = existingQuantity.add(quantity);
            BigDecimal newEntryPrice = existingQuantity.compareTo(BigDecimal.ZERO) == 0
                    ? price
                    : existingEntryPrice.multiply(existingQuantity)
                                        .add(price.multiply(quantity))
                                        .divide(newQuantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
            updatedPosition.setQuantity(newQuantity);
            updatedPosition.setEntryPrice(newEntryPrice);
            updatedPosition.setClosingReservedQuantity(existingReserved);
            updatedPosition.setCumRealizedPnl(existingCumRealized);
            deltaQuantity = quantity;
        } else {
            BigDecimal closeQuantity = quantity.min(existingQuantity);
            BigDecimal remainingQuantity = existingQuantity.subtract(closeQuantity);
            updatedPosition.setQuantity(remainingQuantity);
            updatedPosition.setEntryPrice(existingEntryPrice);
            updatedPosition.setClosingReservedQuantity(existingReserved.subtract(closeQuantity).max(BigDecimal.ZERO));
            BigDecimal direction = position.getSide() == PositionSide.SHORT ? BigDecimal.ONE.negate() : BigDecimal.ONE;
            realizedPnl = price.subtract(existingEntryPrice).multiply(closeQuantity).multiply(direction);
            updatedPosition.setCumRealizedPnl(existingCumRealized.add(realizedPnl));
            deltaQuantity = closeQuantity.negate();

            if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
                updatedPosition.setStatus(PositionStatus.CLOSED);
                updatedPosition.setClosedAt(executedAt);
                eventType = PositionEventType.POSITION_CLOSED;
            }
        }

        int leverage = position.getLeverage() == null ? 1 : position.getLeverage();
        BigDecimal newMargin = updatedPosition.getEntryPrice().multiply(updatedPosition.getQuantity()).multiply(contractMultiplier).abs()
                .divide(BigDecimal.valueOf(leverage), ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
        updatedPosition.setMargin(newMargin);
        updatedPosition.setCumFee(existingCumFee);
        updatedPosition.setCumFundingFee(existingCumFunding);
        BigDecimal deltaMargin = newMargin.subtract(existingMargin);

        BigDecimal effectiveMarkPrice = updatedPosition.getMarkPrice();
        if (effectiveMarkPrice == null || effectiveMarkPrice.compareTo(BigDecimal.ZERO) == 0) {
            updatedPosition.setMarkPrice(price);
            effectiveMarkPrice = price;
        }

        if (updatedPosition.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            if (updatedPosition.getSide() == PositionSide.LONG) {
                updatedPosition.setUnrealizedPnl(effectiveMarkPrice.subtract(updatedPosition.getEntryPrice())
                        .multiply(updatedPosition.getQuantity())
                        .multiply(contractMultiplier));
            } else {
                updatedPosition.setUnrealizedPnl(updatedPosition.getEntryPrice().subtract(effectiveMarkPrice)
                        .multiply(updatedPosition.getQuantity())
                        .multiply(contractMultiplier));
            }
        } else {
            updatedPosition.setUnrealizedPnl(BigDecimal.ZERO);
        }

        if (updatedPosition.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal notional = effectiveMarkPrice.multiply(updatedPosition.getQuantity()).multiply(contractMultiplier).abs();
            if (notional.compareTo(BigDecimal.ZERO) == 0) {
                updatedPosition.setMarginRatio(BigDecimal.ZERO);
            } else {
                updatedPosition.setMarginRatio(updatedPosition.getMargin().add(updatedPosition.getUnrealizedPnl())
                        .divide(notional, ValidationConstant.Names.MARGIN_RATIO_SCALE, RoundingMode.HALF_UP));
            }

            BigDecimal quantityTimesMultiplier = updatedPosition.getQuantity().multiply(contractMultiplier);
            if (updatedPosition.getSide() == PositionSide.LONG) {
                updatedPosition.setLiquidationPrice(
                        updatedPosition.getEntryPrice()
                                .subtract(updatedPosition.getMargin().divide(quantityTimesMultiplier, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP))
                                .divide(BigDecimal.ONE.subtract(maintenanceMarginRate), ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP)
                );
            } else {
                updatedPosition.setLiquidationPrice(
                        updatedPosition.getEntryPrice()
                                .add(updatedPosition.getMargin().divide(quantityTimesMultiplier, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP))
                                .divide(BigDecimal.ONE.add(maintenanceMarginRate), ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP)
                );
            }
        } else {
            updatedPosition.setMarginRatio(BigDecimal.ZERO);
            updatedPosition.setLiquidationPrice(BigDecimal.ZERO);
        }

        return new TradeExecutionData(eventType,
                                      deltaQuantity,
                                      deltaMargin,
                                      realizedPnl,
                                      BigDecimal.ZERO,
                                      BigDecimal.ZERO,
                                      updatedPosition);
    }

    public record TradeSplit(BigDecimal closeQuantity, BigDecimal flipQuantity) {
    }

    @Transactional
    public void updateMarkPrice(@NotNull Long instrumentId, @NotNull BigDecimal markPrice) {
        List<Position> positions = positionRepository.findBy(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getInstrumentId, instrumentId)
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE));

        if (positions.isEmpty()) {
            return;
        }

        BigDecimal contractMultiplier = instrumentCache.get(instrumentId)
                .map(instrument -> instrument.contractSize() != null ? instrument.contractSize() : CONTRACT_MULTIPLIER)
                .orElse(CONTRACT_MULTIPLIER);

        BigDecimal maintenanceMarginRate = riskLimitCache.get(instrumentId)
                .map(riskLimit -> riskLimit.maintenanceMarginRate() != null ? riskLimit.maintenanceMarginRate() : MAINTENANCE_MARGIN_RATE_DEFAULT)
                .orElse(MAINTENANCE_MARGIN_RATE_DEFAULT);

        for (Position position : positions) {
            Position updatedPosition = OpenObjectMapper.convert(position, Position.class);
            updatedPosition.setMarkPrice(markPrice);

            BigDecimal existingQuantity = safe(position.getQuantity());

            if (existingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                if (updatedPosition.getSide() == PositionSide.LONG) {
                    updatedPosition.setUnrealizedPnl(markPrice.subtract(updatedPosition.getEntryPrice())
                            .multiply(existingQuantity)
                            .multiply(contractMultiplier));
                } else {
                    updatedPosition.setUnrealizedPnl(updatedPosition.getEntryPrice().subtract(markPrice)
                            .multiply(existingQuantity)
                            .multiply(contractMultiplier));
                }

                BigDecimal notional = markPrice.multiply(existingQuantity).multiply(contractMultiplier).abs();
                if (notional.compareTo(BigDecimal.ZERO) == 0) {
                    updatedPosition.setMarginRatio(BigDecimal.ZERO);
                } else {
                    updatedPosition.setMarginRatio(updatedPosition.getMargin().add(updatedPosition.getUnrealizedPnl())
                            .divide(notional, ValidationConstant.Names.MARGIN_RATIO_SCALE, RoundingMode.HALF_UP));
                }

                // Liquidation Price = Entry Price - (Margin / Quantity / Multiplier) / (1 - Maintenance Margin Rate) [Long]
                // Liquidation Price = Entry Price + (Margin / Quantity / Multiplier) / (1 + Maintenance Margin Rate) [Short]
                // Note: The original code in other methods seemed to miss contractMultiplier or implied Quantity is contracts?
                // The UPNL calculation explicitly multiplies by contractMultiplier.
                // Assuming standard futures formula: Margin = Notional / Leverage = Price * Quantity * Multiplier / Leverage
                // Liquidation logic usually involves Price where Margin Balance = Maintenance Margin.
                // Here we stick to fixing the dimensional error pointed out in review: Margin / (Quantity * Multiplier).

                BigDecimal quantityTimesMultiplier = existingQuantity.multiply(contractMultiplier);
                if (quantityTimesMultiplier.compareTo(BigDecimal.ZERO) == 0) {
                     updatedPosition.setLiquidationPrice(BigDecimal.ZERO);
                } else {
                    if (updatedPosition.getSide() == PositionSide.LONG) {
                        updatedPosition.setLiquidationPrice(
                                updatedPosition.getEntryPrice()
                                        .subtract(updatedPosition.getMargin().divide(quantityTimesMultiplier, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP))
                                        .divide(BigDecimal.ONE.subtract(maintenanceMarginRate), ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP)
                        );
                    } else {
                        updatedPosition.setLiquidationPrice(
                                updatedPosition.getEntryPrice()
                                        .add(updatedPosition.getMargin().divide(quantityTimesMultiplier, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP))
                                        .divide(BigDecimal.ONE.add(maintenanceMarginRate), ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP)
                        );
                    }
                }
            } else {
                updatedPosition.setUnrealizedPnl(BigDecimal.ZERO);
                updatedPosition.setMarginRatio(BigDecimal.ZERO);
                updatedPosition.setLiquidationPrice(BigDecimal.ZERO);
            }

            int expectedVersion = position.safeVersion();
            updatedPosition.setVersion(expectedVersion + 1);
            positionRepository.updateSelectiveBy(
                    updatedPosition,
                    Wrappers.<PositionPO>lambdaUpdate()
                            .eq(PositionPO::getPositionId, updatedPosition.getPositionId())
                            .eq(PositionPO::getVersion, expectedVersion));
        }
    }

    public record PositionCloseResult(Position position, BigDecimal pnl, BigDecimal marginReleased) {
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record TradeExecutionData(PositionEventType eventType, BigDecimal deltaQuantity,
                                      BigDecimal deltaMargin, BigDecimal realizedPnl,
                                      BigDecimal tradeFee, BigDecimal fundingFee,
                                      Position updatedPosition) {
    }
}
