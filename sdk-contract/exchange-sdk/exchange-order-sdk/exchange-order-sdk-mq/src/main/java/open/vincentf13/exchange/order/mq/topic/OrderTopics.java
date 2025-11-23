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
    ORDER_SUBMITTED(Names.ORDER_SUBMITTED, OrderSubmittedEvent.class),
    ORDER_CANCEL_REQUESTED(Names.ORDER_CANCEL_REQUESTED, OrderCancelRequestedEvent.class),
    ORDER_CREATED(Names.ORDER_CREATED, OrderCreatedEvent.class),
    POSITION_RESERVE_REQUESTED(Names.POSITION_RESERVE_REQUESTED, PositionReserveRequestedEvent.class);

    private final String topic;
    private final Class<?> eventType;

    public static final class Names {
        public static final String ORDER_SUBMITTED = "order.submitted";
        public static final String ORDER_CANCEL_REQUESTED = "order.cancel-requested";
        public static final String ORDER_CREATED = "order.created";
        public static final String POSITION_RESERVE_REQUESTED = "order.position-reserve-requested";

        private Names() {
        }
    }
}
