package open.vincentf13.exchange.order.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.mq.event.OrderCancelRequestedEvent;
import open.vincentf13.exchange.order.mq.event.OrderSubmittedEvent;
import open.vincentf13.exchange.order.mq.topic.OrderTopics;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRequestedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionTopics;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentType;
import open.vincentf13.sdk.core.OpenMapstruct;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final MqOutboxRepository outboxRepository;

    public void publishOrderSubmitted(Order order) {
        OrderSubmittedEvent payload = OpenMapstruct.map(order, OrderSubmittedEvent.class);
        outboxRepository.append(OrderTopics.ORDER_SUBMITTED,
                order.getOrderId(),
                payload,
                null);
    }

    public void publishOrderCancelRequested(Order order, Instant requestedAt, String reason) {
        OrderCancelRequestedEvent payload = new OrderCancelRequestedEvent(
                order.getOrderId(),
                order.getUserId(),
                requestedAt,
                reason
        );
        outboxRepository.append(OrderTopics.ORDER_CANCEL_REQUESTED,
                order.getOrderId(),
                payload,
                null);
    }

    public void publishPositionReserveRequested(Order order, PositionIntentType intentType) {
        PositionReserveRequestedEvent event = new PositionReserveRequestedEvent(
                order.getOrderId(),
                order.getUserId(),
                order.getInstrumentId(),
                order.getSide(),
                intentType,
                order.getQuantity(),
                Instant.now()
        );
        outboxRepository.append(PositionTopics.POSITION_RESERVE_REQUESTED,
                order.getOrderId(),
                event,
                null);
    }
}
