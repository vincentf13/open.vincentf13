package open.vincentf13.exchange.position.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.service.PositionDomainService;
import open.vincentf13.exchange.position.infra.messaging.publisher.PositionEventPublisher;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionEventRepository;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.mq.event.PositionInvalidFillEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionMarginReleasedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionUpdatedEvent;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;
import open.vincentf13.sdk.core.OpenValidator;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        
        // 只處理平倉
        if (intentType == null || intentType == PositionIntentType.INCREASE) {
            return;
        }

        
        // 冪等效驗
        String baseReferenceId = tradeId + ":" + orderSide.name();
        if (positionEventRepository.existsByReference(PositionReferenceType.TRADE, baseReferenceId)) {
            return;
        }

        Instant eventTime = executedAt == null ? Instant.now() : executedAt;
        BigDecimal executedQuantity = safe(quantity);
        if (executedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal totalFee = safe(fee);

        Position position = positionRepository.findOne(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getUserId, userId)
                        .eq(PositionPO::getInstrumentId, instrumentId)
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
                .orElse(null);
        
        // 判斷當前數量 夠不夠平倉
        // 若不夠，就是 開倉 flip 的流程，導致平倉預扣的倉位被 flip 吃掉了。  [詳細需了解 flip流程]
        if (position == null || safe(position.getQuantity()).compareTo(BigDecimal.ZERO) <= 0) {
            // 平倉轉開倉，要補保證金，若保證金為負，風控要限制後續下單並強平。
            publishInvalidFill(tradeId, orderId, userId, instrumentId, asset, orderSide, price, executedQuantity, totalFee, eventTime);
            return;
        }

        BigDecimal existingQuantity = safe(position.getQuantity());
        BigDecimal closeQuantity = executedQuantity.min(existingQuantity);
        BigDecimal openQuantity = executedQuantity.subtract(closeQuantity);

        BigDecimal closeFee = totalFee;
        BigDecimal openFee = BigDecimal.ZERO;
        if (openQuantity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal openRatio = openQuantity.divide(executedQuantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
            openFee = totalFee.multiply(openRatio);
            closeFee = totalFee.subtract(openFee);
        }

        // 平能平的數量，若不夠平，轉開倉 [會不夠平，是因為 flip 流程吃掉，需了解其流程]
        if (closeQuantity.compareTo(BigDecimal.ZERO) > 0) {
            PositionDomainService.PositionCloseResult result = positionDomainService.closePosition(
                    userId,
                    instrumentId,
                    price,
                    closeQuantity,
                    closeFee,
                    BigDecimal.ZERO,
                    orderSide,
                    tradeId,
                    eventTime,
                    false
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

        if (openQuantity.compareTo(BigDecimal.ZERO) > 0) {
            publishInvalidFill(tradeId, orderId, userId, instrumentId, asset, orderSide, price, openQuantity, openFee, eventTime);
        }
    }

    private void publishInvalidFill(Long tradeId,
                                    Long orderId,
                                    Long userId,
                                    Long instrumentId,
                                    AssetSymbol asset,
                                    OrderSide orderSide,
                                    BigDecimal price,
                                    BigDecimal quantity,
                                    BigDecimal feeCharged,
                                    Instant executedAt) {
        positionEventPublisher.publishInvalidFill(new PositionInvalidFillEvent(
                tradeId,
                orderId,
                userId,
                instrumentId,
                asset,
                orderSide,
                price,
                quantity,
                feeCharged,
                executedAt
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
