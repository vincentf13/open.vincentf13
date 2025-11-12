package open.vincentf13.exchange.order.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.config.OrderEventTopicsProperties;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.domain.model.OrderEventType;
import open.vincentf13.exchange.order.infra.messaging.event.OrderCancelRequestedEvent;
import open.vincentf13.exchange.order.infra.messaging.event.OrderSubmittedEvent;
import open.vincentf13.exchange.order.infra.messaging.mapper.OrderEventMessageMapper;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final OrderEventTopicsProperties topics;
    private final MqOutboxRepository outboxRepository;
    private final OrderEventMessageMapper mapper;

    public void publishOrderSubmitted(Order order) {
        String topic = topics.getOrderSubmitted();
        if (!StringUtils.hasText(topic)) {
            log.warn("Order submitted topic is not configured; skipping publish for order {}", order.getId());
            return;
        }
        OrderSubmittedEvent payload = mapper.toOrderSubmittedEvent(order);
        outboxRepository.append(topic,
                order.getId(),
                payload,
                Map.of("eventType", OrderEventType.ORDER_SUBMITTED.name()));
    }

    public void publishOrderCancelRequested(Order order, Instant requestedAt, String reason) {
        String topic = topics.getOrderCancelRequested();
        if (!StringUtils.hasText(topic)) {
            log.warn("Order cancel topic is not configured; skipping publish for order {}", order.getId());
            return;
        }
        OrderCancelRequestedEvent payload = mapper.toOrderCancelRequestedEvent(order, requestedAt, reason);
        outboxRepository.append(topic,
                order.getId(),
                payload,
                Map.of("eventType", OrderEventType.ORDER_CANCEL_REQUESTED.name()));
    }
}
