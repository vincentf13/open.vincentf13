package open.vincentf13.exchange.position.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRejectedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReservedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionTopics;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PositionEventPublisher {
    
    private final MqOutboxRepository outboxRepository;
    
    public void publishReserved(PositionReservedEvent event) {
        outboxRepository.append(PositionTopics.POSITION_RESERVED, event.orderId(), event, null);
    }
    
    public void publishRejected(PositionReserveRejectedEvent event) {
        outboxRepository.append(PositionTopics.POSITION_RESERVE_REJECTED, event.orderId(), event, null);
    }
}
