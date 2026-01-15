package open.vincentf13.exchange.position.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.mq.event.TradeMarginSettledEvent;
import open.vincentf13.exchange.common.sdk.constants.ScaleConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.service.PositionDomainService;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.messaging.publisher.PositionEventPublisher;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionEventRepository;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.mq.event.PositionCloseToOpenCompensationEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionMarginReleasedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionUpdatedEvent;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentResponse;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionReservationReleaseRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.mapper.OpenObjectMapper;
import open.vincentf13.sdk.core.validator.OpenValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class PositionCommandService {

  private final PositionRepository positionRepository;
  private final PositionEventPublisher positionEventPublisher;
  private final PositionEventRepository positionEventRepository;
  private final PositionDomainService positionDomainService;

  public PositionIntentResponse determineIntentAndReserve(
      @NotNull @Valid PositionIntentRequest request) {
    PositionDomainService.PositionIntentResult result =
        positionDomainService.processIntent(
            request.userId(),
            request.getInstrumentId(),
            request.side(),
            request.getQuantity(),
            request.clientOrderId());

    BigDecimal existing =
        result.position() == null ? BigDecimal.ZERO : result.position().getQuantity();

    if (result.errorMessage() != null) {
      return PositionIntentResponse.ofRejected(
          result.intentType(), existing, result.errorMessage());
    }

    return PositionIntentResponse.of(
        result.intentType(),
        existing,
        result.position() == null
            ? null
            : OpenObjectMapper.convert(result.position(), PositionResponse.class));
  }

  public String releaseReservation(@NotNull @Valid PositionReservationReleaseRequest request) {
    if (request.clientOrderId() == null) {
      return "No reservation record found.";
    }
    String referencePrefix = request.clientOrderId() + ":";
    var reservationEvent =
        positionEventRepository.findLatestByReferencePrefix(
            PositionReferenceType.RESERVATION, referencePrefix);
    if (reservationEvent == null || reservationEvent.getReferenceId() == null) {
      return "No reservation record found.";
    }
    String referenceId = reservationEvent.getReferenceId();
    String[] parts = referenceId.split(":", 2);
    if (parts.length < 2 || parts[1].isBlank() || parts[1].contains(":")) {
      return "No reservation record found.";
    }
    PositionSide side;
    try {
      side = PositionSide.valueOf(parts[1]);
    } catch (IllegalArgumentException ex) {
      return "No reservation record found.";
    }
    Position position =
        positionRepository
            .findOne(
                Wrappers.lambdaQuery(PositionPO.class)
                    .eq(PositionPO::getPositionId, reservationEvent.getPositionId())
                    .eq(PositionPO::getSide, side)
                    .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
            .orElse(null);
    if (position == null) {
      return "Reservation already released; position settled by fills in the flip flow for closing.";
    }
    BigDecimal reserved =
        position.getClosingReservedQuantity() == null
            ? BigDecimal.ZERO
            : position.getClosingReservedQuantity();
    if (reserved.compareTo(request.quantity()) < 0) {
      return "Reservation already released; position settled by fills in the flip flow for closing.";
    }
    positionDomainService.releaseClosingPosition(
        position, request.quantity(), request.clientOrderId());
    return "Reservation released.";
  }

  @Transactional
  public void handleTradeExecuted(@NotNull @Valid TradeExecutedEvent event) {
    OpenValidator.validateOrThrow(event);
    processClose(
        event.tradeId(),
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
    processClose(
        event.tradeId(),
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

  @Transactional(rollbackFor = Exception.class)
  public void handleTradeMarginSettled(@NotNull @Valid TradeMarginSettledEvent event) {
    Collection<Position> positions =
        positionDomainService.openPosition(
            event.userId(),
            event.instrumentId(),
            event.orderId(),
            event.asset(),
            event.side(),
            event.price(),
            event.quantity(),
            event.marginUsed(),
            event.feeCharged(),
            event.tradeId(),
            event.executedAt(),
            event.isRecursive());

    for (Position position : positions) {
      positionEventPublisher.publishUpdated(
          new PositionUpdatedEvent(
              position.getUserId(),
              position.getInstrumentId(),
              position.getSide(),
              position.getQuantity(),
              position.getEntryPrice(),
              position.getMarkPrice(),
              position.getUnrealizedPnl(),
              position.getLiquidationPrice(),
              event.settledAt()));
    }
  }

  private void processClose(
      Long tradeId,
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
      throw OpenException.of(
          PositionErrorCode.DUPLICATE_REQUEST,
          Map.of("referenceId", baseReferenceId, "userId", userId));
    }

    Instant eventTime = executedAt == null ? Instant.now() : executedAt;
    if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }

    Position position =
        positionRepository
            .findOne(
                Wrappers.lambdaQuery(PositionPO.class)
                    .eq(PositionPO::getUserId, userId)
                    .eq(PositionPO::getInstrumentId, instrumentId)
                    .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
            .orElse(null);

    // 判斷當前數量 夠不夠平倉
    // 若不夠，就是 flip 的流程，搶奪了預佔倉位

    // [ 當前沒倉位] || [ 當前有倉位 && 但與下平倉單時方向相同 ]  -> 平倉轉開倉
    if (position == null
        || safe(position.getQuantity()).compareTo(BigDecimal.ZERO) <= 0
        || position.getSide() == PositionSide.fromOrderSide(orderSide)) {
      // 平倉轉開倉，要補保證金，若保證金為負，風控要限制後續下單並強平。
      positionEventPublisher.publishCloseToOpenCompensation(
          new PositionCloseToOpenCompensationEvent(
              tradeId,
              orderId,
              userId,
              instrumentId,
              asset,
              orderSide,
              price,
              quantity,
              fee,
              eventTime));
      return;
    }

    // [ 當前有倉位 && 與下單時方向不同 ]
    
    // 只要 position 還有數量 -> 凍結倉位 一定有凍結倒的倉位可以平倉
    // 若 position 的數量比平倉單少，代表被少的數量被  flip 搶走了
    BigDecimal closeQuantity = position.getQuantity().min(quantity);
    // 被 flip 搶走的數量，要平倉轉開倉
    BigDecimal openQuantity = quantity.subtract(closeQuantity);
    BigDecimal closeFee = fee;
    BigDecimal openFee = BigDecimal.ZERO;
    // 平倉轉開倉
    if (openQuantity.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal openRatio =
          openQuantity.divide(
                  quantity, ScaleConstant.COMMON_SCALE, RoundingMode.HALF_UP);
      openFee = fee.multiply(openRatio);
      closeFee = fee.subtract(openFee);
      positionEventPublisher.publishCloseToOpenCompensation(
          new PositionCloseToOpenCompensationEvent(
              tradeId,
              orderId,
              userId,
              instrumentId,
              asset,
              orderSide,
              price,
              openQuantity,
              openFee,
              eventTime));
    }
    
    // 平倉能平的數量
    if (closeQuantity.compareTo(BigDecimal.ZERO) > 0) {
      PositionDomainService.PositionCloseResult result =
          positionDomainService.closePosition(
              userId,
              instrumentId,
              price,
              closeQuantity,
              closeFee,
              orderSide,
              tradeId,
              eventTime,
              false);

      Position updatedPosition = result.position();

      positionEventPublisher.publishUpdated(
          new PositionUpdatedEvent(
              updatedPosition.getUserId(),
              updatedPosition.getInstrumentId(),
              updatedPosition.getSide(),
              updatedPosition.getQuantity(),
              updatedPosition.getEntryPrice(),
              updatedPosition.getMarkPrice(),
              updatedPosition.getUnrealizedPnl(),
              updatedPosition.getLiquidationPrice(),
              eventTime));

      positionEventPublisher.publishMarginReleased(
          new PositionMarginReleasedEvent(
              tradeId,
              orderId,
              userId,
              instrumentId,
              asset,
              updatedPosition.getSide(),
              result.marginReleased(),
              result.feeCharged(),
              result.pnl(),
              eventTime));
    }
  }

  private BigDecimal safe(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
