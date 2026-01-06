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
        BigDecimal existingQuantity = position.getQuantity();
        return position.getSide() != targetSide && existingQuantity.compareTo(quantity) < 0;
    }

    public TradeSplit calculateTradeSplit(Position position, BigDecimal quantity) {
        BigDecimal closeQty = position.getQuantity();
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
            Position updatedPosition = applyPositionUpdate(position,
                                                          instrumentId,
                                                          PositionUpdateInput.forOpen(price, quantity, marginUsed, feeCharged))
                    .position();
            
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
                    position.getQuantity().compareTo(BigDecimal.ZERO) == 0 ? PositionEventType.POSITION_OPENED : PositionEventType.POSITION_INCREASED,
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

        if (quantity.compareTo(position.getQuantity()) > 0) {
            throw OpenException.of(PositionErrorCode.POSITION_INSUFFICIENT_AVAILABLE,
                                   Map.of("userId", userId,
                                          "instrumentId", instrumentId,
                                          "requestedQuantity", quantity,
                                          "closingReservedQuantity", position.getClosingReservedQuantity(),
                                          "positionQuantity", position.getQuantity()));
        }
        
        // 一般平倉 效驗保留倉位數量
        if (!isFlip) {
            if (quantity.compareTo(position.getClosingReservedQuantity()) > 0) {
                throw OpenException.of(PositionErrorCode.POSITION_INSUFFICIENT_AVAILABLE,
                                       Map.of("userId", userId,
                                              "instrumentId", instrumentId,
                                              "requestedQuantity", quantity,
                                              "closingReservedQuantity", position.getClosingReservedQuantity(),
                                              "positionQuantity", position.getQuantity()));
            }
        }

        // 計算盈虧
        PositionUpdateResult updateResult = applyPositionUpdate(position,
                                                               instrumentId,
                                                               PositionUpdateInput.forClose(price, quantity, feeCharged, isFlip, executedAt));
        Position updatedPosition = updateResult.position();
        CloseMetrics closeMetrics = updateResult.closeMetrics();

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
                updatedPosition.getQuantity().compareTo(BigDecimal.ZERO) == 0 ? PositionEventType.POSITION_CLOSED : PositionEventType.POSITION_DECREASED,
                payload,
                orderSide,
                tradeId,
                executedAt,
                false
        );
        positionEventRepository.insert(event);

        return new PositionCloseResult(updatedPosition, closeMetrics.pnl(), closeMetrics.marginReleased());
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

        List<PositionRepository.PositionUpdateTask> updateTasks = new ArrayList<>(positions.size());
        for (Position position : positions) {
            Position updatedPosition = applyPositionUpdate(position,
                                                          instrumentId,
                                                          PositionUpdateInput.forMarkPrice(markPrice))
                    .position();

            int expectedVersion = position.safeVersion();
            updatedPosition.setVersion(expectedVersion + 1);
            updateTasks.add(new PositionRepository.PositionUpdateTask(updatedPosition, expectedVersion));
        }
        positionRepository.updateSelectiveBatch(updateTasks);
    }

    public record PositionCloseResult(Position position, BigDecimal pnl, BigDecimal marginReleased) {
    }

    private enum PositionUpdateType {
        OPEN,
        CLOSE,
        MARK_PRICE
    }

    private record CloseMetrics(BigDecimal marginReleased, BigDecimal pnl) {
    }

    private record PositionUpdateResult(Position position, CloseMetrics closeMetrics) {
    }

    private record PositionUpdateInput(PositionUpdateType type,
                                       BigDecimal tradePrice,
                                       BigDecimal quantity,
                                       BigDecimal marginDelta,
                                       BigDecimal feeCharged,
                                       boolean isFlip,
                                       Instant executedAt) {
        static PositionUpdateInput forOpen(BigDecimal tradePrice,
                                           BigDecimal quantity,
                                           BigDecimal marginDelta,
                                           BigDecimal feeCharged) {
            return new PositionUpdateInput(PositionUpdateType.OPEN,
                    tradePrice,
                    quantity,
                    marginDelta,
                    feeCharged,
                    false,
                    null);
        }

        static PositionUpdateInput forClose(BigDecimal tradePrice,
                                            BigDecimal quantity,
                                            BigDecimal feeCharged,
                                            boolean isFlip,
                                            Instant executedAt) {
            return new PositionUpdateInput(PositionUpdateType.CLOSE,
                    tradePrice,
                    quantity,
                    null,
                    feeCharged,
                    isFlip,
                    executedAt);
        }

        static PositionUpdateInput forMarkPrice(BigDecimal markPrice) {
            return new PositionUpdateInput(PositionUpdateType.MARK_PRICE,
                    markPrice,
                    BigDecimal.ZERO,
                    null,
                    null,
                    false,
                    null);
        }
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

    private BigDecimal resolveMaintenanceMarginRate(Long instrumentId) {
        return riskLimitCache.get(instrumentId)
                .map(riskLimit -> riskLimit.maintenanceMarginRate() != null ? riskLimit.maintenanceMarginRate() : MAINTENANCE_MARGIN_RATE_DEFAULT)
                .orElse(MAINTENANCE_MARGIN_RATE_DEFAULT);
    }

    private BigDecimal resolveEffectiveMarkPrice(Position position,
                                                 Long instrumentId,
                                                 BigDecimal fallbackPrice) {
        markPriceCache.get(instrumentId)
                .ifPresent(position::setMarkPrice);
        BigDecimal markPrice = position.getMarkPrice();
        if (markPrice == null || markPrice.compareTo(BigDecimal.ZERO) == 0) {
            position.setMarkPrice(fallbackPrice);
            return fallbackPrice;
        }
        return markPrice;
    }

    private PositionUpdateResult applyPositionUpdate(Position position,
                                                     Long instrumentId,
                                                     PositionUpdateInput input) {
        Position updatedPosition = OpenObjectMapper.convert(position, Position.class);
        CloseMetrics closeMetrics = null;
        switch (input.type()) {
            case OPEN -> {
                BigDecimal newQuantity = position.getQuantity().add(input.quantity());
                BigDecimal newEntryPrice = position.getEntryPrice()
                        .multiply(position.getQuantity())
                        .add(input.tradePrice().multiply(input.quantity()))
                        .divide(newQuantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
                BigDecimal newMargin = position.getMargin().add(input.marginDelta());
                updatedPosition.setEntryPrice(newEntryPrice);
                updatedPosition.setQuantity(newQuantity);
                updatedPosition.setMargin(newMargin);
                updatedPosition.setClosingReservedQuantity(position.getClosingReservedQuantity());
                updatedPosition.setCumFee(position.getCumFee().add(input.feeCharged()));
                updatedPosition.setStatus(PositionStatus.ACTIVE);
                updatedPosition.setClosedAt(null);
            }
            case CLOSE -> {
                closeMetrics = calculateCloseMetrics(position, input.tradePrice(), input.quantity(), instrumentId);
                BigDecimal newQuantity = position.getQuantity().subtract(input.quantity());
                BigDecimal newMargin = position.getMargin().subtract(closeMetrics.marginReleased());
                updatedPosition.setEntryPrice(position.getEntryPrice());
                updatedPosition.setQuantity(newQuantity);
                updatedPosition.setMargin(newMargin);
                if (input.isFlip()) {
                    updatedPosition.setClosingReservedQuantity(BigDecimal.ZERO);
                } else {
                    updatedPosition.setClosingReservedQuantity(position.getClosingReservedQuantity()
                            .subtract(input.quantity())
                            .max(BigDecimal.ZERO));
                }
                updatedPosition.setCumRealizedPnl(position.getCumRealizedPnl().add(closeMetrics.pnl()));
                updatedPosition.setCumFee(position.getCumFee().add(input.feeCharged()));
                if (newQuantity.compareTo(BigDecimal.ZERO) == 0) {
                    updatedPosition.setStatus(PositionStatus.CLOSED);
                    updatedPosition.setClosedAt(input.executedAt());
                } else {
                    updatedPosition.setStatus(PositionStatus.ACTIVE);
                    updatedPosition.setClosedAt(null);
                }
            }
            case MARK_PRICE -> {
            }
        }

        updateRiskMetrics(updatedPosition, instrumentId, input.tradePrice());
        return new PositionUpdateResult(updatedPosition, closeMetrics);
    }

    private void updateRiskMetrics(Position updatedPosition,
                                   Long instrumentId,
                                   BigDecimal fallbackPrice) {
        BigDecimal contractMultiplier = requireContractSize(instrumentId);
        BigDecimal maintenanceMarginRate = resolveMaintenanceMarginRate(instrumentId);
        BigDecimal effectiveMarkPrice = resolveEffectiveMarkPrice(updatedPosition, instrumentId, fallbackPrice);
        BigDecimal newQuantity = updatedPosition.getQuantity();
        if (newQuantity.compareTo(BigDecimal.ZERO) > 0) {
            if (updatedPosition.getSide() == PositionSide.LONG) {
                updatedPosition.setUnrealizedPnl(effectiveMarkPrice.subtract(updatedPosition.getEntryPrice())
                                                           .multiply(newQuantity)
                                                           .multiply(contractMultiplier));
            } else {
                updatedPosition.setUnrealizedPnl(updatedPosition.getEntryPrice().subtract(effectiveMarkPrice)
                                                  .multiply(newQuantity)
                                                  .multiply(contractMultiplier));
            }

            BigDecimal notional = effectiveMarkPrice.multiply(newQuantity).multiply(contractMultiplier).abs();
            if (notional.compareTo(BigDecimal.ZERO) == 0) {
                updatedPosition.setMarginRatio(BigDecimal.ZERO);
            } else {
                updatedPosition.setMarginRatio(updatedPosition.getMargin().add(updatedPosition.getUnrealizedPnl())
                                               .divide(notional, ValidationConstant.Names.MARGIN_RATIO_SCALE, RoundingMode.HALF_UP));
            }

            BigDecimal quantityTimesMultiplier = newQuantity.multiply(contractMultiplier);
            if (quantityTimesMultiplier.compareTo(BigDecimal.ZERO) == 0) {
                updatedPosition.setLiquidationPrice(null);
                return;
            }
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
            updatedPosition.setUnrealizedPnl(BigDecimal.ZERO);
            updatedPosition.setMarginRatio(BigDecimal.ZERO);
            updatedPosition.setLiquidationPrice(null);
        }
    }

    private CloseMetrics calculateCloseMetrics(Position position,
                                               BigDecimal price,
                                               BigDecimal quantity,
                                               Long instrumentId) {
        BigDecimal existingQuantity = position.getQuantity();
        BigDecimal marginReleased = position.getMargin()
                .multiply(quantity)
                .divide(existingQuantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
        BigDecimal contractMultiplier = requireContractSize(instrumentId);
        BigDecimal pnl = calculateRealizedPnl(position.getSide(),
                position.getEntryPrice(),
                price,
                quantity,
                contractMultiplier);
        return new CloseMetrics(marginReleased, pnl);
    }

    private BigDecimal calculateRealizedPnl(PositionSide side,
                                            BigDecimal entryPrice,
                                            BigDecimal price,
                                            BigDecimal quantity,
                                            BigDecimal contractMultiplier) {
        if (side == PositionSide.LONG) {
            return price.subtract(entryPrice)
                        .multiply(quantity)
                        .multiply(contractMultiplier);
        }
        return entryPrice.subtract(price)
                         .multiply(quantity)
                         .multiply(contractMultiplier);
    }

}
