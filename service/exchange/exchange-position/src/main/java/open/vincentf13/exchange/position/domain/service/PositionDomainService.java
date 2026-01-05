package open.vincentf13.exchange.position.domain.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.model.PositionEvent;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.cache.InstrumentCache;
import open.vincentf13.exchange.position.infra.cache.MarkPriceCache;
import open.vincentf13.exchange.position.infra.cache.RiskLimitCache;
import open.vincentf13.exchange.position.infra.messaging.publisher.PositionEventPublisher;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionEventRepository;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.mq.event.PositionMarginReleasedEvent;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionEventType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
public class PositionDomainService {

    private final PositionRepository positionRepository;
    private final PositionEventRepository positionEventRepository;
    private final PositionEventPublisher positionEventPublisher;
    private final MarkPriceCache markPriceCache;
    private final RiskLimitCache riskLimitCache;
    private final InstrumentCache instrumentCache;

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
        BigDecimal existingQuantity = safe(position.getQuantity());
        return position.getSide() != targetSide && existingQuantity.compareTo(quantity) < 0;
    }

    public TradeSplit calculateTradeSplit(Position position, BigDecimal quantity) {
        BigDecimal closeQty = safe(position.getQuantity());
        BigDecimal flipQty = quantity.subtract(closeQty);
        return new TradeSplit(closeQty, flipQty);
    }

    public Collection<Position> openPosition(@NotNull Long userId,
                                             @NotNull Long instrumentId,
                                             @NotNull Long orderId,
                                             @NotNull AssetSymbol asset,
                                             @NotNull OrderSide orderSide,
                                             @NotNull @DecimalMin(value = ValidationConstant.Names.PRICE_MIN, inclusive = false) BigDecimal price,
                                             @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = false) BigDecimal quantity,
                                             @NotNull BigDecimal marginUsed,
                                             @NotNull BigDecimal feeCharged,
                                             @NotNull Long tradeId,
                                             @NotNull Instant executedAt,
                                             boolean isRecursive) {
        String referenceId = tradeId + ":" + orderSide.name();
        if (isRecursive) {
            referenceId += ":FLIP";
        }
        
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
                .orElseGet(() -> Position.createDefault(userId, instrumentId, side, requireDefaultLeverage(instrumentId)));

        // 開倉 因併發訂單，變為平倉，需釋放保證金，並處理flip的情況
        if (position.getSide() != side) {
            if (shouldSplitTrade(position, side, quantity)) {
                TradeSplit split = calculateTradeSplit(position, quantity);

                BigDecimal flipRatio = split.flipQuantity().divide(quantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
                BigDecimal flipMargin = marginUsed.multiply(flipRatio);
                BigDecimal closeMargin = marginUsed.subtract(flipMargin);

                BigDecimal flipFeeCharged = feeCharged.multiply(flipRatio);
                BigDecimal closeFeeCharged = feeCharged.subtract(flipFeeCharged);

                List<Position> results = new ArrayList<>();
                results.addAll(openPosition(userId, instrumentId, orderId, asset, orderSide, price, split.closeQuantity(), closeMargin, closeFeeCharged, tradeId, executedAt,false));
                results.addAll(openPosition(userId, instrumentId, orderId, asset, orderSide, price, split.flipQuantity(), flipMargin, flipFeeCharged, tradeId, executedAt,true));
                return results;
            }

            PositionCloseResult result = closePosition(userId, instrumentId, price, quantity, feeCharged, orderSide, tradeId, executedAt, true);
            positionEventPublisher.publishMarginReleased(new PositionMarginReleasedEvent(
                    tradeId,
                    orderId,
                    userId,
                    instrumentId,
                    asset,
                    position.getSide(),
                    result.marginReleased(),
                    result.pnl(),
                    executedAt
            ));
    
            return Collections.singletonList(result.position());
        } else {
            Position updatedPosition = OpenObjectMapper.convert(position, Position.class);
            
            markPriceCache.get(instrumentId)
                          .ifPresent(updatedPosition::setMarkPrice);
            
            BigDecimal contractMultiplier = requireContractSize(instrumentId);
            
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
            updatedPosition.setCumFee(existingCumFee.add(feeCharged));
            
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
            
            String payload = Position.buildPayload(position, updatedPosition);
            PositionEvent event = PositionEvent.createTradeEvent(
                    updatedPosition.getPositionId(),
                    userId,
                    instrumentId,
                    existingQuantity.compareTo(BigDecimal.ZERO) == 0 ? PositionEventType.POSITION_OPENED : PositionEventType.POSITION_INCREASED,
                    payload,
                    orderSide,
                    tradeId,
                    executedAt,
                    isRecursive
                                                                );
            positionEventRepository.insert(event);
            
            return Collections.singletonList(updatedPosition);
        }
    }

    public PositionCloseResult closePosition(Long userId, Long instrumentId,
                                             BigDecimal price, BigDecimal quantity,
                                             BigDecimal feeCharged,
                                             OrderSide orderSide,
                                             Long tradeId, Instant executedAt,
                                             boolean isFlip) {
        String referenceId = tradeId + ":" + orderSide.name();

        Position position = positionRepository.findOne(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getUserId, userId)
                        .eq(PositionPO::getInstrumentId, instrumentId)
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
                .orElseThrow(() -> OpenException.of(PositionErrorCode.POSITION_NOT_FOUND,
                        Map.of("userId", userId, "instrumentId", instrumentId)));
        
        // 冪等效驗
        if (positionEventRepository.existsByReference(PositionReferenceType.TRADE, referenceId)) {
            return new PositionCloseResult(position, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        Position updatedPosition = OpenObjectMapper.convert(position, Position.class);

        
        BigDecimal reserved = safe(position.getClosingReservedQuantity());
        BigDecimal existingQty = safe(position.getQuantity());
        
        
        if (quantity.compareTo(existingQty) > 0) {
            throw OpenException.of(PositionErrorCode.POSITION_INSUFFICIENT_AVAILABLE,
                                   Map.of("userId", userId,
                                          "instrumentId", instrumentId,
                                          "requestedQuantity", quantity,
                                          "closingReservedQuantity", reserved,
                                          "positionQuantity", existingQty));
        }
        
        // 一般平倉 效驗保留倉位數量
        if (!isFlip) {
            if (quantity.compareTo(reserved) > 0) {
                throw OpenException.of(PositionErrorCode.POSITION_INSUFFICIENT_AVAILABLE,
                                       Map.of("userId", userId,
                                              "instrumentId", instrumentId,
                                              "requestedQuantity", quantity,
                                              "closingReservedQuantity", reserved,
                                              "positionQuantity", existingQty));
            }
            updatedPosition.setClosingReservedQuantity(reserved.subtract(quantity).max(BigDecimal.ZERO));
        } else {
            // Flip
            updatedPosition.setClosingReservedQuantity(BigDecimal.ZERO);
        }
        
        // 更新 Mark Price
        markPriceCache.get(instrumentId)
                .ifPresent(updatedPosition::setMarkPrice);
        BigDecimal effectiveMarkPrice = updatedPosition.getMarkPrice();
        if (effectiveMarkPrice == null || effectiveMarkPrice.compareTo(BigDecimal.ZERO) == 0) {
            effectiveMarkPrice = price;
            updatedPosition.setMarkPrice(price);
        }
        

        BigDecimal contractMultiplier = requireContractSize(instrumentId);

        BigDecimal maintenanceMarginRate = riskLimitCache.get(instrumentId)
                .map(riskLimit -> riskLimit.maintenanceMarginRate() != null ? riskLimit.maintenanceMarginRate() : MAINTENANCE_MARGIN_RATE_DEFAULT)
                .orElse(MAINTENANCE_MARGIN_RATE_DEFAULT);

        BigDecimal existingQuantity = safe(position.getQuantity());
        BigDecimal existingMargin = safe(position.getMargin());
        BigDecimal existingCumFee = safe(position.getCumFee());
        BigDecimal existingCumRealized = safe(position.getCumRealizedPnl());

        
        // 釋放保證金
        BigDecimal marginToRelease = existingMargin.multiply(quantity)
                .divide(existingQuantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);

        // 計算盈虧
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
        // 更新數量、保證金
        updatedPosition.setQuantity(newQuantity);
        updatedPosition.setMargin(existingMargin.subtract(marginToRelease));
        
        
        // 更新統計
        updatedPosition.setCumRealizedPnl(existingCumRealized.add(pnl));
        updatedPosition.setCumFee(existingCumFee.add(feeCharged));

        // 關倉
        if (newQuantity.compareTo(BigDecimal.ZERO) == 0) {
            updatedPosition.setStatus(PositionStatus.CLOSED);
            updatedPosition.setClosedAt(executedAt);
        }

  
        // 未實現盈虧
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

        // 維持保證金率
        if (updatedPosition.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal notional = effectiveMarkPrice.multiply(updatedPosition.getQuantity()).multiply(contractMultiplier).abs();
            if (notional.compareTo(BigDecimal.ZERO) == 0) {
                updatedPosition.setMarginRatio(BigDecimal.ZERO);
            } else {
                updatedPosition.setMarginRatio(updatedPosition.getMargin().add(updatedPosition.getUnrealizedPnl())
                        .divide(notional, ValidationConstant.Names.MARGIN_RATIO_SCALE, RoundingMode.HALF_UP));
            }

            // 強平價格
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

        String payload = Position.buildPayload(position, updatedPosition);
        PositionEvent event = PositionEvent.createTradeEvent(
                updatedPosition.getPositionId(),
                userId,
                instrumentId,
                newQuantity.compareTo(BigDecimal.ZERO) == 0 ? PositionEventType.POSITION_CLOSED : PositionEventType.POSITION_DECREASED,
                payload,
                orderSide,
                tradeId,
                executedAt,
                false
        );
        positionEventRepository.insert(event);

        return new PositionCloseResult(updatedPosition, pnl, marginToRelease);
    }

    public record TradeSplit(BigDecimal closeQuantity, BigDecimal flipQuantity) {
    }

    @Transactional(propagation = Propagation.NEVER)
    public void updateMarkPrice(@NotNull Long instrumentId, @NotNull BigDecimal markPrice) {
        List<Position> positions = positionRepository.findBy(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getInstrumentId, instrumentId)
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE));

        if (positions.isEmpty()) {
            return;
        }

        BigDecimal contractMultiplier = requireContractSize(instrumentId);

        BigDecimal maintenanceMarginRate = riskLimitCache.get(instrumentId)
                .map(riskLimit -> riskLimit.maintenanceMarginRate() != null ? riskLimit.maintenanceMarginRate() : MAINTENANCE_MARGIN_RATE_DEFAULT)
                .orElse(MAINTENANCE_MARGIN_RATE_DEFAULT);

        List<PositionRepository.PositionUpdateTask> updateTasks = new ArrayList<>(positions.size());
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
            updateTasks.add(new PositionRepository.PositionUpdateTask(updatedPosition, expectedVersion));
        }
        positionRepository.updateSelectiveBatch(updateTasks);
    }

    public record PositionCloseResult(Position position, BigDecimal pnl, BigDecimal marginReleased) {
    }

    private BigDecimal requireContractSize(Long instrumentId) {
        return instrumentCache.get(instrumentId)
                .map(instrument -> instrument.contractSize())
                .filter(contractSize -> contractSize != null && contractSize.compareTo(BigDecimal.ZERO) > 0)
                .orElseThrow(() -> new IllegalStateException("Instrument cache missing or invalid contractSize for instrumentId=" + instrumentId));
    }

    private Integer requireDefaultLeverage(Long instrumentId) {
        return instrumentCache.get(instrumentId)
                .map(instrument -> instrument.defaultLeverage())
                .filter(leverage -> leverage != null && leverage > 0)
                .orElseThrow(() -> new IllegalStateException("Instrument cache missing or invalid defaultLeverage for instrumentId=" + instrumentId));
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

}
