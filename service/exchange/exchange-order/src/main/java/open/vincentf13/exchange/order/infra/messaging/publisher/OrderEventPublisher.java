package open.vincentf13.exchange.order.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.mq.event.FundsFreezeRequestedEvent;
import open.vincentf13.exchange.order.mq.event.OrderCreatedEvent;
import open.vincentf13.exchange.order.mq.topic.OrderTopics;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final MqOutboxRepository outboxRepository;

    public void publishFundsFreezeRequested(FundsFreezeRequestedEvent event) {
        outboxRepository.append(OrderTopics.FUNDS_FREEZE_REQUESTED.getTopic(), event.orderId(), event, null);
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        outboxRepository.append(OrderTopics.ORDER_CREATED.getTopic(), event.instrumentId(), event, null);
    }
}
