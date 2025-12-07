package open.vincentf13.exchange.position.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.*;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.messaging.publisher.PositionEventPublisher;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.mq.event.PositionMarginReleasedEvent;
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
        PositionSide side = toPositionSide(orderSide);
        Optional<Position> optional = positionRepository.findOne(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getUserId, userId)
                        .eq(PositionPO::getInstrumentId, instrumentId)
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE));
        Position position = optional.orElseThrow(() -> OpenException.of(PositionErrorCode.POSITION_NOT_FOUND,
                                                                        Map.of("userId", userId, "instrumentId", instrumentId)));
        BigDecimal availableToClose = position.availableToClose();
        if (availableToClose.compareTo(quantity) < 0) {
            throw OpenException.of(PositionErrorCode.POSITION_INSUFFICIENT_AVAILABLE,
                                   Map.of("userId", userId,
                                          "instrumentId", instrumentId,
                                          "requestedQuantity", quantity,
                                          "availableToClose", availableToClose));
        }
        BigDecimal existingQty = position.getQuantity();
        if (existingQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw OpenException.of(PositionErrorCode.POSITION_NOT_FOUND,
                                   Map.of("userId", userId, "instrumentId", instrumentId, "orderId", orderId));
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
        BigDecimal newReserved = safe(position.getClosingReservedQuantity()).subtract(quantity).max(BigDecimal.ZERO);
        int expectedVersion = position.safeVersion();
        Position update = Position.builder()
                                  .positionId(position.getPositionId())
                                  .quantity(newQuantity)
                                  .closingReservedQuantity(newReserved)
                                  .margin(position.getMargin().subtract(marginToRelease))
                                  .cumRealizedPnl(safe(position.getCumRealizedPnl()).add(pnl))
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
