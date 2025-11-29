package open.vincentf13.exchange.position.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.model.PositionEvent;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.PositionLogEvent;
import open.vincentf13.exchange.position.infra.cache.MarkPriceCache;
import open.vincentf13.exchange.position.infra.messaging.publisher.PositionEventPublisher;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionEventRepository;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.mq.event.PositionClosedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionUpdatedEvent;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionLeverageRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionLeverageResponse;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionEventType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckRequest;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckResponse;
import open.vincentf13.exchange.risk.margin.sdk.rest.client.ExchangeRiskMarginClient;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.spring.cloud.openfeign.OpenApiClientInvoker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Validated
public class PositionCommandService {
    
    private final PositionRepository positionRepository;
    private final ExchangeRiskMarginClient riskMarginClient;
    private final PositionEventRepository positionEventRepository;
    private final PositionEventPublisher positionEventPublisher;
    private final MarkPriceCache markPriceCache;

    private final BigDecimal MAINTENANCE_MARGIN_RATE_DEFAULT = BigDecimal.valueOf(0.005);
    
    public PositionReserveOutcome reserveForClose(
            @NotNull Long orderId,
            @NotNull Long userId,
            @NotNull Long instrumentId,
            @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = true) BigDecimal quantity,
            @NotNull PositionSide side
                                                 ) {
        Position position = positionRepository.findOne(
                                                      Wrappers.lambdaQuery(PositionPO.class)
                                                              .eq(PositionPO::getUserId, userId)
                                                              .eq(PositionPO::getInstrumentId, instrumentId)
                                                              .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
                                              .orElse(null);
        if (position == null) {
            return PositionReserveOutcome.rejected("POSITION_NOT_FOUND");
        }
        if (position.availableToClose().compareTo(quantity) < 0) {
            return PositionReserveOutcome.rejected("INSUFFICIENT_AVAILABLE");
        }
        int expectedVersion = position.safeVersion();
        Position updateRecord = Position.builder()
                                        .closingReservedQuantity(position.getClosingReservedQuantity().add(quantity))
                                        .version(expectedVersion + 1)
                                        .build();
        boolean success = positionRepository.updateSelectiveBy(
                updateRecord,
                new LambdaUpdateWrapper<PositionPO>()
                        .eq(PositionPO::getPositionId, position.getPositionId())
                        .eq(PositionPO::getUserId, position.getUserId())
                        .eq(PositionPO::getInstrumentId, position.getInstrumentId())
                        .eq(PositionPO::getSide, side)
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE)
                        .eq(PositionPO::getVersion, expectedVersion)
                                                              );
        if (!success) {
            return PositionReserveOutcome.rejected("RESERVE_FAILED");
        }
        BigDecimal avgOpenPrice = position.getEntryPrice();
        return PositionReserveOutcome.accepted(quantity, avgOpenPrice);
    }
    
    public PositionLeverageResponse adjustLeverage(@NotNull Long userId,
                                                   @NotNull Long instrumentId,
                                                   @Valid PositionLeverageRequest request) {
        Position position = positionRepository.findOne(
                                                      Wrappers.lambdaQuery(PositionPO.class)
                                                              .eq(PositionPO::getUserId, userId)
                                                              .eq(PositionPO::getInstrumentId, instrumentId)
                                                              .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
                                              .orElseGet(() -> positionRepository.createDefault(userId, instrumentId));
        if (position == null) {
            throw OpenException.of(PositionErrorCode.POSITION_NOT_FOUND,
                                   Map.of("instrumentId", instrumentId, "userId", userId));
        }
        
        Integer targetLeverage = request.targetLeverage();
        if (targetLeverage.equals(position.getLeverage())) {
            OpenLog.info(PositionLogEvent.POSITION_LEVERAGE_UNCHANGED, "userId", userId, "instrumentId", instrumentId);
            return new PositionLeverageResponse(position.getLeverage(), Instant.now());
        }
        
        LeveragePrecheckRequest precheckRequest = buildPrecheckRequest(position, targetLeverage);
        LeveragePrecheckResponse precheckResponse = OpenApiClientInvoker.call(
                () -> riskMarginClient.precheckLeverage(precheckRequest),
                msg -> OpenException.of(PositionErrorCode.LEVERAGE_PRECHECK_FAILED,
                                        Map.of("positionId", position.getPositionId(), "remoteMessage", msg))
                                                                             );
        if (!precheckResponse.allow()) {
            throw OpenException.of(PositionErrorCode.LEVERAGE_PRECHECK_FAILED,
                                   buildPrecheckMeta(position.getPositionId(), instrumentId, targetLeverage, precheckResponse));
        }
        
        int expectedVersion = position.safeVersion();
        Position updateRecord = Position.builder()
                                        .leverage(targetLeverage)
                                        .version(expectedVersion + 1)
                                        .build();
        boolean updated = positionRepository.updateSelectiveBy(
                updateRecord,
                new LambdaUpdateWrapper<PositionPO>()
                        .eq(PositionPO::getPositionId, position.getPositionId())
                        .eq(PositionPO::getUserId, position.getUserId())
                        .eq(PositionPO::getInstrumentId, position.getInstrumentId())
                        .eq(PositionPO::getSide, position.getSide())
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE)
                        .eq(PositionPO::getVersion, expectedVersion)
                                                              );
        if (!updated) {
            throw OpenException.of(PositionErrorCode.POSITION_NOT_FOUND,
                                   Map.of("positionId", position.getPositionId(), "instrumentId", instrumentId));
        }
        OpenLog.info(PositionLogEvent.POSITION_LEVERAGE_UPDATED, "positionId", position.getPositionId(), "userId", position.getUserId(), "instrumentId", instrumentId, "fromLeverage", position.getLeverage(), "toLeverage", targetLeverage);
        return new PositionLeverageResponse(targetLeverage, Instant.now());
    }

    @Transactional
    public void handleTradeExecuted(@NotNull TradeExecutedEvent event) {
        processTradeForUser(event.makerUserId(), event.instrumentId(), event.orderSide(),
                event.price(), event.quantity(), event.tradeId(), event.executedAt());

        processTradeForUser(event.takerUserId(), event.instrumentId(), event.counterpartyOrderSide(),
                event.price(), event.quantity(), event.tradeId(), event.executedAt());
    }

    private void processTradeForUser(Long userId, Long instrumentId, OrderSide orderSide,
                                     BigDecimal price, BigDecimal quantity, Long tradeId, Instant executedAt) {
        PositionSide side = toPositionSide(orderSide);

        Position position = positionRepository.findOne(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getUserId, userId)
                        .eq(PositionPO::getInstrumentId, instrumentId)
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
                .orElse(null);

        if (position != null && position.getSide() != side && position.getQuantity().compareTo(quantity) < 0) {
            BigDecimal closeQty = position.getQuantity();
            BigDecimal flipQty = quantity.subtract(closeQty);

            processTradeForUser(userId, instrumentId, orderSide, price, closeQty, tradeId, executedAt);
            processTradeForUser(userId, instrumentId, orderSide, price, flipQty, tradeId, executedAt);
            return;
        }

        if (position == null) {
            position = Position.createDefault(userId, instrumentId, side);
        }

        BigDecimal cachedMarkPrice = markPriceCache.get(instrumentId).orElse(null);
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
            BigDecimal totalCost = position.getEntryPrice().multiply(position.getQuantity())
                    .add(price.multiply(quantity));
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

        BigDecimal margin = position.getEntryPrice().multiply(position.getQuantity()).abs()
                .divide(BigDecimal.valueOf(position.getLeverage()), 12, RoundingMode.HALF_UP);
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
                position.setLiquidationPrice(
                        position.getEntryPrice().subtract(marginPerUnit).divide(BigDecimal.ONE.subtract(mmr), 12, RoundingMode.HALF_UP)
                );
            } else {
                position.setLiquidationPrice(
                        position.getEntryPrice().add(marginPerUnit).divide(BigDecimal.ONE.add(mmr), 12, RoundingMode.HALF_UP)
                );
            }
        } else {
            position.setLiquidationPrice(BigDecimal.ZERO);
        }

        position.setUpdatedAt(executedAt);
        position.setVersion(position.safeVersion() + 1);

        if (position.getPositionId() == null) {
            positionRepository.insertSelective(position);
        } else {
            boolean updated = positionRepository.updateSelectiveBy(
                    position,
                    new LambdaUpdateWrapper<PositionPO>()
                            .eq(PositionPO::getPositionId, position.getPositionId())
                            .eq(PositionPO::getVersion, position.getVersion() - 1)
            );
            if (!updated) {
                throw new RuntimeException("Concurrent update on position " + position.getPositionId());
            }
        }

        PositionEvent event = PositionEvent.builder()
                .positionId(position.getPositionId())
                .userId(userId)
                .instrumentId(instrumentId)
                .eventType(eventType)
                .deltaQuantity(isIncrease ? deltaQuantity : deltaQuantity.negate())
                .deltaPnl(deltaPnl)
                .newQuantity(position.getQuantity())
                .newReservedQuantity(position.getClosingReservedQuantity())
                .newEntryPrice(position.getEntryPrice())
                .newUnrealizedPnl(position.getUnrealizedPnl())
                .referenceId(tradeId)
                .referenceType(PositionReferenceType.TRADE)
                .metadata("")
                .occurredAt(executedAt)
                .createdAt(Instant.now())
                .build();
        positionEventRepository.insert(event);

        if (position.getStatus() == PositionStatus.CLOSED) {
            positionEventPublisher.publishClosed(new PositionClosedEvent(userId, instrumentId, executedAt));
        } else {
            positionEventPublisher.publishUpdated(new PositionUpdatedEvent(
                    userId, instrumentId, position.getSide(), position.getQuantity(),
                    position.getEntryPrice(), position.getMarkPrice(), position.getUnrealizedPnl(),
                    position.getLiquidationPrice(), executedAt
            ));
        }
    }

    private PositionSide toPositionSide(OrderSide orderSide) {
        if (orderSide == null) return null;
        return orderSide == OrderSide.BUY ? PositionSide.LONG : PositionSide.SHORT;
    }
    
    private LeveragePrecheckRequest buildPrecheckRequest(Position position,
                                                         Integer targetLeverage) {
        return new LeveragePrecheckRequest(
                position.getPositionId(),
                position.getInstrumentId(),
                position.getUserId(),
                targetLeverage,
                position.getQuantity(),
                position.getMargin(),
                position.getMarkPrice()
        );
    }
    
    private Map<String, Object> buildPrecheckMeta(Long positionId,
                                                  Long instrumentId,
                                                  Integer targetLeverage,
                                                  LeveragePrecheckResponse response) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("positionId", positionId);
        meta.put("instrumentId", instrumentId);
        meta.put("targetLeverage", targetLeverage);
        if (response != null) {
            meta.put("suggestedLeverage", response.suggestedLeverage());
            meta.put("deficit", response.deficit());
            meta.put("reason", response.reason());
        }
        return meta;
    }
    
    public record PositionReserveResult(boolean success, BigDecimal reservedQuantity, String reason,
                                        Instant processedAt) {
        public static PositionReserveResult accepted(BigDecimal quantity) {
            return new PositionReserveResult(true, quantity, null, Instant.now());
        }
        
        public static PositionReserveResult rejected(String reason) {
            return new PositionReserveResult(false, BigDecimal.ZERO, reason, Instant.now());
        }
        
        public boolean isCloseIntent() {
            return true;
        }
    }
    
    public record PositionReserveOutcome(PositionReserveResult result, BigDecimal avgOpenPrice) {
        public static PositionReserveOutcome accepted(BigDecimal reservedQuantity,
                                                      BigDecimal avgOpenPrice) {
            return new PositionReserveOutcome(PositionReserveResult.accepted(reservedQuantity), avgOpenPrice);
        }
        
        public static PositionReserveOutcome rejected(String reason) {
            return new PositionReserveOutcome(PositionReserveResult.rejected(reason), null);
        }
    }
}
