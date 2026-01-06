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

    public PositionSide toPositionSide(OrderSide orderSide) {
        if (orderSide == null) {
            return null;
        }
        return orderSide == OrderSide.BUY ? PositionSide.LONG : PositionSide.SHORT;
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
                .orElseGet(() -> Position.createDefault(userId, instrumentId, side,
                        InstrumentCache.requireDefaultLeverage(instrumentCache, instrumentId)));

        // 開倉 因併發訂單，變為平倉，需釋放保證金，並處理flip的情況
        if (position.getSide() != side) {
            if (position.shouldSplitTrade(side, quantity)) {
                Position.TradeSplit split = position.calculateTradeSplit(quantity);

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

       
        PositionUpdateResult updateResult = applyPositionUpdate(position,
                                                               instrumentId,
                                                               PositionUpdateInput.forClose(price, quantity, feeCharged, isFlip, executedAt));
        Position updatedPosition = updateResult.position();
        Position.CloseMetrics closeMetrics = updateResult.closeMetrics();

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

    private record PositionUpdateResult(Position position, Position.CloseMetrics closeMetrics) {
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

    private PositionUpdateResult applyPositionUpdate(Position position,
                                                     Long instrumentId,
                                                     PositionUpdateInput input) {
        Position updatedPosition = OpenObjectMapper.convert(position, Position.class);
        BigDecimal contractMultiplier = InstrumentCache.requireContractSize(instrumentCache, instrumentId);
        BigDecimal maintenanceMarginRate = RiskLimitCache.resolveMaintenanceMarginRate(riskLimitCache, instrumentId);
        Position.CloseMetrics closeMetrics = null;
        switch (input.type()) {
            case OPEN -> {
                BigDecimal effectiveMarkPrice = MarkPriceCache.resolveEffectiveMarkPrice(markPriceCache, instrumentId, input.tradePrice());
                updatedPosition.applyOpen(input.tradePrice(),
                        effectiveMarkPrice,
                        input.quantity(),
                        input.marginDelta(),
                        input.feeCharged(),
                        contractMultiplier,
                        maintenanceMarginRate);
            }
            case CLOSE -> {
                BigDecimal effectiveMarkPrice = MarkPriceCache.resolveEffectiveMarkPrice(markPriceCache, instrumentId, input.tradePrice());
                closeMetrics = updatedPosition.applyClose(input.tradePrice(),
                        effectiveMarkPrice,
                        input.quantity(),
                        input.feeCharged(),
                        input.isFlip(),
                        input.executedAt(),
                        contractMultiplier,
                        maintenanceMarginRate);
            }
            case MARK_PRICE -> {
                updatedPosition.applyMarkPrice(input.tradePrice(),
                        contractMultiplier,
                        maintenanceMarginRate);
            }
        }

        return new PositionUpdateResult(updatedPosition, closeMetrics);
    }

}
