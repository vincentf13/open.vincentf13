package open.vincentf13.exchange.position.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.sdk.mq.event.PositionCloseToOpenCompensationEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionMarginReleasedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionOpenToCloseCompensationEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionTopics;
import open.vincentf13.exchange.position.sdk.mq.event.PositionUpdatedEvent;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PositionEventPublisher {

  private final MqOutboxRepository outboxRepository;

  public void publishUpdated(PositionUpdatedEvent event) {
    outboxRepository.append(
        PositionTopics.POSITION_UPDATED.getTopic(), event.userId(), event, null);
  }

  public void publishMarginReleased(PositionMarginReleasedEvent event) {
    outboxRepository.append(
        PositionTopics.POSITION_MARGIN_RELEASED.getTopic(), event.tradeId(), event, null);
  }

  public void publishCloseToOpenCompensation(PositionCloseToOpenCompensationEvent event) {
    outboxRepository.append(
        PositionTopics.POSITION_CLOSE_TO_OPEN_COMPENSATION.getTopic(), event.userId(), event, null);
  }

  public void publishOpenToCloseCompensation(PositionOpenToCloseCompensationEvent event) {
    outboxRepository.append(
        PositionTopics.POSITION_OPEN_TO_CLOSE_COMPENSATION.getTopic(), event.userId(), event, null);
  }
}
