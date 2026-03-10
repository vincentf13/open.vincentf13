package open.vincentf13.exchange.position.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.mq.event.TradeMarginSettledEvent;
import open.vincentf13.exchange.account.sdk.mq.topic.AccountTradeTopics;
import open.vincentf13.exchange.position.service.PositionCommandService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeMarginSettledEventListener {

  private final PositionCommandService positionCommandService;

  @KafkaListener(
      topics = AccountTradeTopics.Names.TRADE_MARGIN_SETTLED,
      groupId = "${exchange.position.trade-settled.consumer-group:exchange-position-trade-settled}")
  public void onTradeMarginSettled(
      @Payload TradeMarginSettledEvent event, Acknowledgment acknowledgment) {
    positionCommandService.handleTradeMarginSettled(event);
    acknowledgment.acknowledge();
  }
}
