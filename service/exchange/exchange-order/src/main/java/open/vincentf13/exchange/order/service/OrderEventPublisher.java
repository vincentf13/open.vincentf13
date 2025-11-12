package open.vincentf13.exchange.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.config.OrderEventTopicsProperties;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.domain.model.OrderEventType;
import open.vincentf13.exchange.order.messaging.OrderCancelRequestedEvent;
import open.vincentf13.exchange.order.messaging.OrderSubmittedEvent;
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

    public void publishOrderSubmitted(Order order) {
        String topic = topics.getOrderSubmitted();
        if (!StringUtils.hasText(topic)) {
            log.warn("Order submitted topic is not configured; skipping publish for order {}", order.getId());
            return;
        }
        OrderSubmittedEvent payload = new OrderSubmittedEvent(
                order.getId(),
                order.getUserId(),
                order.getInstrumentId(),
                order.getSide(),
                order.getType(),
                order.getStatus(),
                order.getTimeInForce(),
                order.getPrice(),
                order.getStopPrice(),
                order.getQuantity(),
                order.getClientOrderId(),
                order.getSource(),
                order.getCreatedAt()
        );
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
        OrderCancelRequestedEvent payload = new OrderCancelRequestedEvent(
                order.getId(),
                order.getUserId(),
                requestedAt,
                reason
        );
        outboxRepository.append(topic,
                order.getId(),
                payload,
                Map.of("eventType", OrderEventType.ORDER_CANCEL_REQUESTED.name()));
    }
}
