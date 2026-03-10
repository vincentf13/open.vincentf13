package open.vincentf13.exchange.account.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.mq.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.account.sdk.mq.event.FundsFrozenEvent;
import open.vincentf13.exchange.account.sdk.mq.topic.AccountFundsTopics;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FundsFreezeEventPublisher {

  private final MqOutboxRepository outboxRepository;

  public void publishFrozen(FundsFrozenEvent event) {
    outboxRepository.append(
        AccountFundsTopics.FUNDS_FROZEN.getTopic(), event.orderId(), event, null);
  }

  public void publishFreezeFailed(FundsFreezeFailedEvent event) {
    outboxRepository.append(
        AccountFundsTopics.FUNDS_FREEZE_FAILED.getTopic(), event.orderId(), event, null);
  }
}
