package open.vincentf13.exchange.position.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.LedgerEntryCreatedEvent;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.service.PositionDomainService;
import open.vincentf13.exchange.position.infra.messaging.publisher.PositionEventPublisher;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.mq.event.PositionClosedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionUpdatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;

@Service
@RequiredArgsConstructor
@Validated
public class PositionCommandService {
    
    private final PositionRepository positionRepository;
    private final PositionEventPublisher positionEventPublisher;
    private final PositionDomainService positionDomainService;
    
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
        
        PositionDomainService.ReserveForCloseResult result =
                positionDomainService.calculateReserveForClose(position, quantity);
        
        if (!result.success()) {
            return PositionReserveOutcome.rejected(result.reason());
        }
        
        int expectedVersion = position.safeVersion();
        Position updateRecord = Position.builder()
                                        .closingReservedQuantity(result.newReservedQuantity())
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
        
        return PositionReserveOutcome.accepted(quantity, result.avgOpenPrice());
    }
    
    @Transactional
    public void handleTradeExecuted(@NotNull TradeExecutedEvent event) {
        processTradeForUser(event.makerUserId(), event.instrumentId(), event.orderSide(),
                            event.price(), event.quantity(), event.tradeId(), event.executedAt());
        
        processTradeForUser(event.takerUserId(), event.instrumentId(), event.counterpartyOrderSide(),
                            event.price(), event.quantity(), event.tradeId(), event.executedAt());
    }

    @Transactional
    public void handleLedgerEntryCreated(@NotNull LedgerEntryCreatedEvent event) {
        if (event.userId() == null) {
            return;
        }
        
        Position position = positionDomainService.processLedgerEntry(event);
        if (position != null) {
            positionEventPublisher.publishUpdated(new PositionUpdatedEvent(
                    event.userId(), event.instrumentId(), position.getSide(), position.getQuantity(),
                    position.getEntryPrice(), position.getMarkPrice(), position.getUnrealizedPnl(),
                    position.getLiquidationPrice(), event.eventTime()
            ));
        }
    }
    
    private void processTradeForUser(@NotNull Long userId,
                                     @NotNull Long instrumentId,
                                     @NotNull OrderSide orderSide,
                                     @NotNull @DecimalMin(value = ValidationConstant.Names.PRICE_MIN, inclusive = false) BigDecimal price,
                                     @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = false) BigDecimal quantity,
                                     @NotNull Long tradeId,
                                     @NotNull Instant executedAt) {
        Collection<Position> positions = positionDomainService.processTradeForUser(
                userId, instrumentId, orderSide, price, quantity, tradeId, executedAt);

        for (Position position : positions) {
            positionEventPublisher.publishUpdated(new PositionUpdatedEvent(
                    userId, instrumentId, position.getSide(), position.getQuantity(),
                    position.getEntryPrice(), position.getMarkPrice(), position.getUnrealizedPnl(),
                    position.getLiquidationPrice(), executedAt
            ));

            if (position.getStatus() == PositionStatus.CLOSED) {
                positionEventPublisher.publishClosed(new PositionClosedEvent(userId, instrumentId, executedAt));
            }
        }
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
