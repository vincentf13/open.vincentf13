package open.vincentf13.exchange.position.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.sdk.mq.event.PositionTopics;
import open.vincentf13.exchange.position.sdk.mq.event.PositionInvalidFillEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionMarginReleasedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionUpdatedEvent;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PositionEventPublisher {
    
    private final MqOutboxRepository outboxRepository;
    
    public void publishUpdated(PositionUpdatedEvent event) {
        outboxRepository.append(PositionTopics.POSITION_UPDATED.getTopic(), event.userId(), event, null);
    }
    
    public void publishMarginReleased(PositionMarginReleasedEvent event) {
        outboxRepository.append(PositionTopics.POSITION_MARGIN_RELEASED.getTopic(), event.tradeId(), event, null);
    }

    public void publishInvalidFill(PositionInvalidFillEvent event) {
        outboxRepository.append(PositionTopics.POSITION_INVALID_FILL.getTopic(), event.userId(), event, null);
    }
}
