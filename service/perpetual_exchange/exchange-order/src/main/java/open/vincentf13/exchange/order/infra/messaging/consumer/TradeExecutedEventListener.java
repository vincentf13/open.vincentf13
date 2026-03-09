package open.vincentf13.exchange.order.infra.messaging.consumer;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import open.vincentf13.exchange.order.service.OrderCommandService;
import open.vincentf13.sdk.core.validator.OpenValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeExecutedEventListener {

  private final OrderCommandService orderCommandService;

  @KafkaListener(
      topics = MatchingTopics.Names.TRADE_EXECUTED,
      groupId = "${open.vincentf13.exchange.order.consumer-group:exchange-order}")
  public void onTradeExecuted(@Payload TradeExecutedEvent event, Acknowledgment acknowledgment) {
    OpenValidator.validateOrThrow(event);
    applyFill(
        event.tradeId(),
        event.orderId(),
        event.price(),
        event.quantity(),
        event.makerFee(),
        event.executedAt());
    applyFill(
        event.tradeId(),
        event.counterpartyOrderId(),
        event.price(),
        event.quantity(),
        event.takerFee(),
        event.executedAt());
    acknowledgment.acknowledge();
  }

  private void applyFill(
      Long tradeId,
      Long targetOrderId,
      BigDecimal price,
      BigDecimal filledQuantity,
      BigDecimal feeDelta,
      Instant executedAt) {
    orderCommandService.processTradeExecution(
        targetOrderId, tradeId, price, filledQuantity, feeDelta, executedAt);
  }
}
