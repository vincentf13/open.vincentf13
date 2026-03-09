package open.vincentf13.exchange.account.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.mq.event.TradeMarginSettledEvent;
import open.vincentf13.exchange.account.sdk.mq.topic.AccountTradeTopics;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeSettlementEventPublisher {

  private final MqOutboxRepository outboxRepository;

  public void publishTradeMarginSettled(TradeMarginSettledEvent event) {
    outboxRepository.append(
        AccountTradeTopics.TRADE_MARGIN_SETTLED.getTopic(), event.orderId(), event, null);
  }
}
