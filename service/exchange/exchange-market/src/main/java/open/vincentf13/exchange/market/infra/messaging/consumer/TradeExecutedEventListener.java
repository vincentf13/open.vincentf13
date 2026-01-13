package open.vincentf13.exchange.market.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.infra.cache.TickerStatsCacheService;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import open.vincentf13.sdk.core.validator.OpenValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeExecutedEventListener {

  private final TickerStatsCacheService tickerStatsCacheService;

  @KafkaListener(
      topics = MatchingTopics.Names.TRADE_EXECUTED,
      groupId = "${open.vincentf13.exchange.market.trade.consumer-group:exchange-market-trade}")
  public void onTradeExecuted(@Payload TradeExecutedEvent event, Acknowledgment acknowledgment) {
    try {
      OpenValidator.validateOrThrow(event);
    } catch (Exception e) {
      acknowledgment.acknowledge();
      return;
    }
    tickerStatsCacheService.recordTrade(
        event.instrumentId(), event.tradeId(), event.price(), event.quantity(), event.executedAt());
    acknowledgment.acknowledge();
  }
}
