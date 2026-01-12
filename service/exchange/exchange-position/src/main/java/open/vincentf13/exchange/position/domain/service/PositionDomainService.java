package open.vincentf13.exchange.position.domain.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.*;
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
import open.vincentf13.exchange.position.sdk.mq.event.PositionOpenToCloseCompensationEvent;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionEventType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
    
    public PositionIntentResult processIntent(Long userId,
                                              Long instrumentId,
                                              PositionSide side,
                                              BigDecimal quantity,
                                              String clientOrderId) {
        var activePosition = positionRepository.findOne(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getUserId, userId)
                        .eq(PositionPO::getInstrumentId, instrumentId)
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE));
        
        // 會有紀錄，代表之前已判斷為平倉，且已凍結倉位。
        // 後續風控拒絕，需解凍倉位
        if (clientOrderId != null
            && positionEventRepository.existsByReferenceLike(PositionReferenceType.RESERVATION, clientOrderId + ":")) {
            return new PositionIntentResult(PositionIntentType.REDUCE, activePosition.orElse(null), null);
        }
        
        
        if (activePosition.isEmpty()) {
            return new PositionIntentResult(PositionIntentType.INCREASE, null, null);
        }
        
        Position position = activePosition.get();
        PositionIntentType intentType = position.evaluateIntent(side, quantity);
        
        try {
            if (intentType != PositionIntentType.INCREASE) {
                reserveClosingPosition(position, quantity, clientOrderId);
            }
            return new PositionIntentResult(intentType, position, null);
        } catch (OpenException e) {
            return new PositionIntentResult(intentType, position, e.getCode().message());
        }
    }
    
    public record PositionIntentResult(PositionIntentType intentType, Position position, String errorMessage) {
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void reserveClosingPosition(Position position,
                                       BigDecimal reservedQuantity,
                                       String clientOrderId) {
        
        BigDecimal availableToClose = position.availableToClose();
        // prevent flip when all quantity is reserved for closing.
        if (availableToClose.compareTo(BigDecimal.ZERO) <= 0) {
            throw OpenException.of(PositionErrorCode.POSITION_INSUFFICIENT_AVAILABLE);
        }
        if (availableToClose.compareTo(reservedQuantity) < 0) {
            throw OpenException.of(PositionErrorCode.POSITION_INSUFFICIENT_AVAILABLE);
        }
        
        Position originalPosition = OpenObjectMapper.convert(position, Position.class);
        int expectedVersion = position.safeVersion();
        position.setClosingReservedQuantity(position.getClosingReservedQuantity().add(reservedQuantity));
        position.setVersion(expectedVersion + 1);
        boolean updated = positionRepository.updateSelectiveBy(
                position,
                Wrappers.<PositionPO>lambdaUpdate()
                        .eq(PositionPO::getPositionId, position.getPositionId())
                        .eq(PositionPO::getUserId, position.getUserId())
                        .eq(PositionPO::getInstrumentId, position.getInstrumentId())
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE)
                        .eq(PositionPO::getVersion, expectedVersion));
        if (!updated) {
            throw OpenException.of(PositionErrorCode.POSITION_CONCURRENT_UPDATE);
        }
        
        String payload = Position.buildPayload(originalPosition, position);
        PositionEvent event = PositionEvent.createReservationEvent(
                position.getPositionId(),
                position.getUserId(),
                position.getInstrumentId(),
                payload,
                clientOrderId + ":" + reservedQuantity,
                Instant.now()
                                                                  );
        positionEventRepository.insert(event);
    }

    @Transactional(rollbackFor = Exception.class)
    public void releaseClosingPosition(Position position,
                                       BigDecimal reservedQuantity,
                                       String clientOrderId) {
        String referenceId =  clientOrderId + ":release:" + reservedQuantity;
        if ( positionEventRepository.existsByReference(PositionReferenceType.RESERVATION, referenceId)) {
            return;
        }
        
        // TODO flip 搶奪了預扣倉位

        Position originalPosition = OpenObjectMapper.convert(position, Position.class);
        int expectedVersion = position.safeVersion();
        BigDecimal currentReserved = position.getClosingReservedQuantity() == null ? BigDecimal.ZERO : position.getClosingReservedQuantity();
        position.setClosingReservedQuantity(currentReserved.subtract(reservedQuantity));
        position.setVersion(expectedVersion + 1);
        boolean updated = positionRepository.updateSelectiveBy(
                position,
                Wrappers.<PositionPO>lambdaUpdate()
                        .eq(PositionPO::getPositionId, position.getPositionId())
                        .eq(PositionPO::getUserId, position.getUserId())
                        .eq(PositionPO::getInstrumentId, position.getInstrumentId())
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE)
                        .eq(PositionPO::getVersion, expectedVersion));
        if (!updated) {
            throw OpenException.of(PositionErrorCode.POSITION_CONCURRENT_UPDATE);
        }

        String payload = Position.buildPayload(originalPosition, position);
        PositionEvent event = PositionEvent.createReservationEvent(
                position.getPositionId(),
                position.getUserId(),
                position.getInstrumentId(),
                payload,
                referenceId,
                Instant.now()
                                                                  );
        positionEventRepository.insert(event);
    }
    
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
            throw OpenException.of(PositionErrorCode.DUPLICATE_REQUEST,
                                   Map.of("referenceId", referenceId, "userId", userId));
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
                /**
                 在發生「反手」（Flip）操作時，邏輯會變得比較複雜，因為這涉及到倉位權限的爭奪與資金補償。
                 
                 1. 「搶奪」預凍結倉位 (Stealing Reserved Quantity)
                 通常情況下，如果用戶已經下了一個「平倉單」，系統會先在該倉位上標記 closingReservedQuantity（預扣平倉數量），
                 這部分數量是被「鎖定」的，不能再被其他平倉操作使用。
                 
                 但在 Flip（反手） 流程中，用戶發送的是一個巨大的「反向開倉單」。為了保證交易能連續執行（先全平再開反向），
                 Flip流程具有最高優先權，它會強制把該倉位上「所有的現有數量」（包含那些已經被其他訂單預扣的數量）全部拿來做平倉。
                 
                 2. 原本平倉流程的失效
                 當 Flip 「搶走」了這些預扣數量並完成成交後，原本那些還在掛單中的「平倉單」實際上已經沒有倉位可以平了。
                 
                 3. 為什麼要做開倉補償
                 * 開倉單的視角： 該 Flip 訂單在 Account 服務眼中是一筆「開倉」，所以預扣了開倉保證金。
                 * 實際執行的視角： 在 Position 服務中，這筆訂單的前半段（closeQty）實際上執行了「平倉」。
                 * 矛盾點： 平倉是不應該扣除「新保證金」的。
                 * 結論： 因此，當 Flip 流程「搶過來」平倉時，我們必須發送一個補償消息，告訴 Account
                 
                 服務：「這筆訂單雖然名義上是開倉，但實際上它幫我平掉了舊倉位，所以請把原本預扣的那部分『開倉保證金』退還給用戶。」
                 
                 4. 為什麼要做平倉補償
                 * 因為平倉的預凍結倉位，被 Flip 流程 Stealing 了 預扣艙位，平倉成交結算時，將會沒有倉位可供結算
                 * 所以進行補償流程，平倉轉開倉
                 * 但需注意，開倉保證金不足時，需另外處理
                 */
                Position.TradeSplit split = position.calculateTradeSplit(quantity);
                
                BigDecimal flipRatio = split.flipQuantity().divide(quantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
                BigDecimal flipMargin = marginUsed.multiply(flipRatio);
                BigDecimal closeMargin = marginUsed.subtract(flipMargin);
                
                BigDecimal flipFeeCharged = feeCharged.multiply(flipRatio);
                BigDecimal closeFeeCharged = feeCharged.subtract(flipFeeCharged);
                
                List<Position> results = new ArrayList<>();
                results.addAll(openPosition(userId, instrumentId, orderId, asset, orderSide, price, split.closeQuantity(), closeMargin, closeFeeCharged, tradeId, executedAt, false));
                results.addAll(openPosition(userId, instrumentId, orderId, asset, orderSide, price, split.flipQuantity(), flipMargin, flipFeeCharged, tradeId, executedAt, true));
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
                    result.feeCharged(),
                    result.pnl(),
                    executedAt
            ));
            
            positionEventPublisher.publishOpenToCloseCompensation(new PositionOpenToCloseCompensationEvent(
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
    
    public PositionCloseResult closePosition(Long userId,
                                             Long instrumentId,
                                             BigDecimal price,
                                             BigDecimal quantity,
                                             BigDecimal feeCharged,
                                             OrderSide orderSide,
                                             Long tradeId,
                                             Instant executedAt,
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
            throw OpenException.of(PositionErrorCode.DUPLICATE_REQUEST,
                                   Map.of("referenceId", referenceId, "userId", userId));
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
        
        return new PositionCloseResult(updatedPosition, closeMetrics.pnl(), closeMetrics.marginReleased(), feeCharged);
    }
    
    @Transactional(propagation = Propagation.NEVER)
    public void updateMarkPrice(@NotNull Long instrumentId,
                                @NotNull BigDecimal markPrice) {
        List<Position> positions = positionRepository.findBy(
                Wrappers.lambdaQuery(PositionPO.class)
                        .eq(PositionPO::getInstrumentId, instrumentId)
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE));
        
        if (positions.isEmpty()) {
            return;
        }
        
        List<PositionRepository.PositionUpdateTask> updateTasks = new ArrayList<>(positions.size());
        List<PositionEvent> eventTasks = new ArrayList<>();
        Instant now = Instant.now();
        
        for (Position position : positions) {
            // 5秒內不重複更新同一倉位的標記價相關指標，避免頻繁寫入
            if (position.getUpdatedAt() != null &&
                        Duration.between(position.getUpdatedAt(), now).toMillis() < 5000) {
                continue;
            }
            Position updatedPosition = applyPositionUpdate(position,
                                                           instrumentId,
                                                           PositionUpdateInput.forMarkPrice(markPrice))
                                               .position();
            
            int expectedVersion = position.safeVersion();
            updatedPosition.setVersion(expectedVersion + 1);
            updateTasks.add(new PositionRepository.PositionUpdateTask(updatedPosition, expectedVersion));
            
            String payload = Position.buildPayload(position, updatedPosition);
            if (!"{}".equals(payload)) {
                eventTasks.add(PositionEvent.createMarkPriceEvent(
                        updatedPosition.getPositionId(),
                        updatedPosition.getUserId(),
                        updatedPosition.getInstrumentId(),
                        payload,
                        now
                                                                 ));
            }
        }
        positionRepository.updateSelectiveBatch(updateTasks);
        // 不紀錄
        //positionEventRepository.insertBatch(eventTasks);
    }
    
    public record PositionCloseResult(Position position, BigDecimal pnl, BigDecimal marginReleased,
                                      BigDecimal feeCharged) {
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
                updatedPosition.applyOpen(input.tradePrice(),
                                          input.tradePrice(),
                                          input.quantity(),
                                          input.marginDelta(),
                                          input.feeCharged(),
                                          contractMultiplier,
                                          maintenanceMarginRate);
            }
            case CLOSE -> {
                closeMetrics = updatedPosition.applyClose(input.tradePrice(),
                                                          input.tradePrice(),
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
