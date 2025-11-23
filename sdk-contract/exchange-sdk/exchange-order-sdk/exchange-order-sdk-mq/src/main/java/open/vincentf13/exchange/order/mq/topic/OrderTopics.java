package open.vincentf13.exchange.order.mq.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.mq.event.OrderCancelRequestedEvent;
import open.vincentf13.exchange.order.mq.event.OrderCreatedEvent;
import open.vincentf13.exchange.order.mq.event.OrderSubmittedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRequestedEvent;

@Getter
@RequiredArgsConstructor
public enum OrderTopics {
    ORDER_SUBMITTED("order.submitted", OrderSubmittedEvent.class),
    ORDER_CANCEL_REQUESTED("order.cancel-requested", OrderCancelRequestedEvent.class),
    ORDER_CREATED("order.created", OrderCreatedEvent.class),
    POSITION_RESERVE_REQUESTED("order.position-reserve-requested", PositionReserveRequestedEvent.class);

    private final String topic;
    private final Class<?> eventType;
}
