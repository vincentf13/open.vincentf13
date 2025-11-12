package open.vincentf13.exchange.order.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.domain.model.OrderEventType;
import open.vincentf13.exchange.order.infra.messaging.mapper.OrderEventMapper;
import open.vincentf13.exchange.order.mq.event.OrderCancelRequestedEvent;
import open.vincentf13.exchange.order.mq.event.OrderSubmittedEvent;
import open.vincentf13.exchange.order.mq.topic.OrderTopics;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final MqOutboxRepository outboxRepository;
    private final OrderEventMapper mapper;

    public void publishOrderSubmitted(Order order) {
        OrderSubmittedEvent payload = mapper.toOrderSubmittedEvent(order);
        outboxRepository.append(OrderTopics.ORDER_SUBMITTED,
                order.getId(),
                payload,
                null);
    }

    public void publishOrderCancelRequested(Order order, Instant requestedAt, String reason) {
        OrderCancelRequestedEvent payload = mapper.toOrderCancelRequestedEvent(order, requestedAt, reason);
        outboxRepository.append(OrderTopics.ORDER_CANCEL_REQUESTED,
                order.getId(),
                payload,
                null);
    }
}
