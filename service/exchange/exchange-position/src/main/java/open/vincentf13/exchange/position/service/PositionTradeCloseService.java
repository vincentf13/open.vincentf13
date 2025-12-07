package open.vincentf13.exchange.position.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.*;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.model.PositionEvent;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.messaging.publisher.PositionEventPublisher;
import open.vincentf13.exchange.position.infra.cache.InstrumentCache;
import open.vincentf13.exchange.position.infra.cache.MarkPriceCache;
import open.vincentf13.exchange.position.infra.cache.RiskLimitCache;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionEventRepository;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.mq.event.PositionMarginReleasedEvent;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionEventType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.exception.OpenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PositionTradeCloseService {
    
    private final PositionRepository positionRepository;
    private final PositionEventPublisher positionEventPublisher;
    private final MarkPriceCache markPriceCache;
    private final RiskLimitCache riskLimitCache;
    private final InstrumentCache instrumentCache;
    private final PositionEventRepository positionEventRepository;
    
    private static final BigDecimal MAINTENANCE_MARGIN_RATE_DEFAULT = BigDecimal.valueOf(0.005);
    private static final BigDecimal CONTRACT_MULTIPLIER_DEFAULT = BigDecimal.ONE;
    
    @Transactional
    public void handleTradeExecuted(@NotNull @Valid TradeExecutedEvent event) {
        OpenValidator.validateOrThrow(event);
        processParticipant(event.tradeId(),
                           event.orderId(),
                           event.makerUserId(),
                           event.orderSide(),
                           event.makerIntent(),
                           event.price(),
                           event.quantity(),
                           event.quoteAsset(),
                           event.instrumentId(),
                           event.executedAt());
        processParticipant(event.tradeId(),
                           event.counterpartyOrderId(),
                           event.takerUserId(),
                           event.counterpartyOrderSide(),
                           event.takerIntent(),
                           event.price(),
                           event.quantity(),
                           event.quoteAsset(),
                           event.instrumentId(),
                           event.executedAt());
    }
    
    private void processParticipant(Long tradeId,
                                    Long orderId,
                                    Long userId,
                                    OrderSide orderSide,
                                    PositionIntentType intentType,
                                    BigDecimal price,
                                    BigDecimal quantity,
                                    AssetSymbol asset,
                                    Long instrumentId,
                                    Instant executedAt) {
        if (intentType == null || intentType == PositionIntentType.INCREASE) {
            return;
        }
        if (positionEventRepository.existsByReferenceAndUser(PositionReferenceType.TRADE, tradeId, userId)) {
            return;
        }
        PositionSide side = toPositionSide(orderSide);
        Optional<Position> optional = positionRepository.findOne(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getUserId, userId)
                        .eq(PositionPO::getInstrumentId, instrumentId)
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE));
        Position position = optional.orElseThrow(() -> OpenException.of(PositionErrorCode.POSITION_NOT_FOUND,
                                                                        Map.of("userId", userId, "instrumentId", instrumentId)));
        BigDecimal reserved = safe(position.getClosingReservedQuantity());
        BigDecimal existingQty = safe(position.getQuantity());
        if (quantity.compareTo(reserved) > 0 || quantity.compareTo(existingQty) > 0) {
            throw OpenException.of(PositionErrorCode.POSITION_INSUFFICIENT_AVAILABLE,
                                   Map.of("userId", userId,
                                          "instrumentId", instrumentId,
                                          "requestedQuantity", quantity,
                                          "closingReservedQuantity", reserved,
                                          "positionQuantity", existingQty));
        }
        BigDecimal marginToRelease = position.getMargin()
                                             .multiply(quantity)
                                             .divide(existingQty, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
        BigDecimal pnl;
        if (position.getSide() == PositionSide.LONG) {
            pnl = price.subtract(position.getEntryPrice())
                       .multiply(quantity);
        } else {
            pnl = position.getEntryPrice().subtract(price)
                       .multiply(quantity);
        }
        BigDecimal newQuantity = existingQty.subtract(quantity);
        BigDecimal newReserved = reserved.subtract(quantity);
        BigDecimal contractMultiplier = instrumentCache.get(instrumentId)
                                                       .map(instrument -> instrument.contractSize() != null ? instrument.contractSize() : CONTRACT_MULTIPLIER_DEFAULT)
                                                       .orElse(CONTRACT_MULTIPLIER_DEFAULT);
        BigDecimal maintenanceMarginRate = riskLimitCache.get(instrumentId)
                                                         .map(riskLimit -> riskLimit.maintenanceMarginRate() != null ? riskLimit.maintenanceMarginRate() : MAINTENANCE_MARGIN_RATE_DEFAULT)
                                                         .orElse(MAINTENANCE_MARGIN_RATE_DEFAULT);
        Optional<BigDecimal> latestMark = markPriceCache.get(instrumentId);
        BigDecimal markPrice = latestMark.orElse(price);
        BigDecimal unrealizedPnl = position.getUnrealizedPnl();
        BigDecimal marginRatio = position.getMarginRatio();
        BigDecimal liquidationPrice = position.getLiquidationPrice();
        BigDecimal remainingMargin = position.getMargin().subtract(marginToRelease);
        BigDecimal feeDelta = safe(position.getCumFee()).add(isMaker ? event.makerFee() : event.takerFee());
        if (markPrice != null && newQuantity.compareTo(BigDecimal.ZERO) > 0) {
            unrealizedPnl = position.getSide() == PositionSide.LONG
                            ? markPrice.subtract(position.getEntryPrice()).multiply(newQuantity).multiply(contractMultiplier)
                            : position.getEntryPrice().subtract(markPrice).multiply(newQuantity).multiply(contractMultiplier);
            BigDecimal notional = markPrice.multiply(newQuantity).abs();
            marginRatio = notional.compareTo(BigDecimal.ZERO) == 0
                          ? BigDecimal.ZERO
                          : remainingMargin.add(unrealizedPnl)
                                            .divide(notional, ValidationConstant.Names.MARGIN_RATIO_SCALE, RoundingMode.HALF_UP);
            BigDecimal marginPerUnit = remainingMargin.divide(newQuantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
            if (position.getSide() == PositionSide.LONG) {
                liquidationPrice = position.getEntryPrice()
                                           .subtract(marginPerUnit)
                                           .divide(BigDecimal.ONE.subtract(maintenanceMarginRate), ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
            } else {
                liquidationPrice = position.getEntryPrice()
                                           .add(marginPerUnit)
                                           .divide(BigDecimal.ONE.add(maintenanceMarginRate), ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
            }
        }
        int expectedVersion = position.safeVersion();
        Position update = Position.builder()
                                  .positionId(position.getPositionId())
                                  .quantity(newQuantity)
                                  .closingReservedQuantity(newReserved)
                                  .margin(position.getMargin().subtract(marginToRelease))
                                  .cumRealizedPnl(safe(position.getCumRealizedPnl()).add(pnl))
                                  .cumFee(feeDelta)
                                  .markPrice(markPrice)
                                  .unrealizedPnl(unrealizedPnl)
                                  .marginRatio(marginRatio)
                                  .liquidationPrice(liquidationPrice)
                                  .status(newQuantity.compareTo(BigDecimal.ZERO) == 0 ? PositionStatus.CLOSED : position.getStatus())
                                  .version(expectedVersion + 1)
                                  .build();
        boolean updated = positionRepository.updateSelectiveBy(
                update,
                Wrappers.<PositionPO>lambdaUpdate()
                        .eq(PositionPO::getPositionId, position.getPositionId())
                        .eq(PositionPO::getUserId, userId)
                        .eq(PositionPO::getInstrumentId, instrumentId)
                        .eq(PositionPO::getStatus, position.getStatus())
                        .eq(PositionPO::getVersion, expectedVersion));
        if (!updated) {
            throw OpenException.of(PositionErrorCode.POSITION_CONCURRENT_UPDATE,
                                   Map.of("positionId", position.getPositionId()));
        }
        positionEventRepository.insert(PositionEvent.createTradeEvent(
                position.getPositionId(),
                userId,
                instrumentId,
                newQuantity.compareTo(BigDecimal.ZERO) == 0 ? PositionEventType.POSITION_CLOSED : PositionEventType.POSITION_DECREASED,
                quantity.negate(),
                marginToRelease.negate(),
                pnl,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                newQuantity,
                newReserved,
                position.getEntryPrice(),
                position.getLeverage(),
                update.getMargin(),
                unrealizedPnl,
                liquidationPrice,
                tradeId,
                executedAt == null ? Instant.now() : executedAt
        ));
        positionEventPublisher.publishMarginReleased(new PositionMarginReleasedEvent(
                tradeId,
                orderId,
                userId,
                instrumentId,
                asset,
                position.getSide(),
                marginToRelease,
                pnl,
                executedAt == null ? Instant.now() : executedAt
        ));
    }
    
    private PositionSide toPositionSide(OrderSide orderSide) {
        if (orderSide == null) {
            return null;
        }
        return orderSide == OrderSide.BUY ? PositionSide.LONG : PositionSide.SHORT;
    }
    
    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
