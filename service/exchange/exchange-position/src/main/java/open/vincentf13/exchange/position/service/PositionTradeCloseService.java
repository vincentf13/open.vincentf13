package open.vincentf13.exchange.position.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.service.PositionDomainService;
import open.vincentf13.exchange.position.infra.messaging.publisher.PositionEventPublisher;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionEventRepository;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.mq.event.PositionMarginReleasedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionUpdatedEvent;
import open.vincentf13.sdk.core.OpenValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PositionTradeCloseService {
    
    private final PositionRepository positionRepository;
    private final PositionEventPublisher positionEventPublisher;
    private final PositionEventRepository positionEventRepository;
    private final PositionDomainService positionDomainService;
    
    @Transactional
    public void handleTradeExecuted(@NotNull @Valid TradeExecutedEvent event) {
        OpenValidator.validateOrThrow(event);
        processClose(event.tradeId(),
                     event.orderId(),
                     event.makerUserId(),
                     event.orderSide(),
                     event.makerIntent(),
                     event.price(),
                     event.quantity(),
                     event.quoteAsset(),
                     event.instrumentId(),
                     event.makerFee(),
                     event.executedAt());
        processClose(event.tradeId(),
                     event.counterpartyOrderId(),
                     event.takerUserId(),
                     event.counterpartyOrderSide(),
                     event.takerIntent(),
                     event.price(),
                     event.quantity(),
                     event.quoteAsset(),
                     event.instrumentId(),
                     event.takerFee(),
                     event.executedAt());
    }
    
    private void processClose(Long tradeId,
                              Long orderId,
                              Long userId,
                              OrderSide orderSide,
                              PositionIntentType intentType,
                              BigDecimal price,
                              BigDecimal quantity,
                              AssetSymbol asset,
                              Long instrumentId,
                              BigDecimal fee,
                              Instant executedAt) {
        if (intentType == null || intentType == PositionIntentType.INCREASE) {
            return;
        }

        Instant eventTime = executedAt == null ? Instant.now() : executedAt;
        PositionDomainService.PositionCloseResult result = positionDomainService.closePosition(
                userId,
                instrumentId,
                price,
                quantity,
                safe(fee),
                BigDecimal.ZERO,
                orderSide,
                tradeId,
                eventTime,
                true
        );

        Position updatedPosition = result.position();

        positionEventPublisher.publishUpdated(new PositionUpdatedEvent(
                updatedPosition.getUserId(),
                updatedPosition.getInstrumentId(),
                updatedPosition.getSide(),
                updatedPosition.getQuantity(),
                updatedPosition.getEntryPrice(),
                updatedPosition.getMarkPrice(),
                updatedPosition.getUnrealizedPnl(),
                updatedPosition.getLiquidationPrice(),
                eventTime
        ));

        positionEventPublisher.publishMarginReleased(new PositionMarginReleasedEvent(
                tradeId,
                orderId,
                userId,
                instrumentId,
                asset,
                updatedPosition.getSide(),
                result.marginReleased(),
                result.pnl(),
                eventTime
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
