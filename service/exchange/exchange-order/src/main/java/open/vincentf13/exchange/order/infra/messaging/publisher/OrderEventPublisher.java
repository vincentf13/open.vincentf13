package open.vincentf13.exchange.order.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.mq.event.OrderCreatedEvent;
import open.vincentf13.exchange.order.mq.event.OrderSubmittedEvent;
import open.vincentf13.exchange.order.mq.topic.OrderTopics;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRequestedEvent;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionIntentType;
import open.vincentf13.sdk.core.OpenMapstruct;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final MqOutboxRepository outboxRepository;

    public void publishOrderSubmitted(Order order) {
        OrderSubmittedEvent payload = OpenMapstruct.map(order, OrderSubmittedEvent.class);
        outboxRepository.append(OrderTopics.ORDER_SUBMITTED.getTopic(),
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
        outboxRepository.append(OrderTopics.POSITION_RESERVE_REQUESTED.getTopic(),
                order.getOrderId(),
                event,
                null);
    }

    public void publishOrderCreated(Order order, String frozenAsset, BigDecimal frozenAmount) {
        OrderCreatedEvent payload = new OrderCreatedEvent(
                order.getOrderId(),
                order.getUserId(),
                order.getInstrumentId(),
                order.getSide(),
                order.getType(),
                order.getPrice(),
                order.getQuantity(),
                order.getClientOrderId(),
                frozenAsset,
                frozenAmount,
                Instant.now()
        );
        outboxRepository.append(OrderTopics.ORDER_CREATED.getTopic(),
                order.getOrderId(),
                payload,
                null);
    }
}
