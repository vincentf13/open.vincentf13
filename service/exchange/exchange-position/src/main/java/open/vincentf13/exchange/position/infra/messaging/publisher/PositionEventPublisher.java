package open.vincentf13.exchange.position.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRejectedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReservedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionTopics;
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

 

    public void publishReserved(PositionReservedEvent event) {
        outboxRepository.append(PositionTopics.POSITION_RESERVED.getTopic(), event.orderId(), event, null);
    }

    public void publishReserveRejected(PositionReserveRejectedEvent event) {
        outboxRepository.append(PositionTopics.POSITION_RESERVE_REJECTED.getTopic(), event.orderId(), event, null);
    }

    public boolean hasEvent(String topic, Long key) {
        return outboxRepository.exists(topic, key);
    }
}
