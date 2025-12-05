package open.vincentf13.exchange.order.mq.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.mq.event.OrderCancelRequestedEvent;
import open.vincentf13.exchange.order.mq.event.OrderCreatedEvent;
import open.vincentf13.exchange.order.mq.event.FundsFreezeRequestedEvent;

@Getter
@RequiredArgsConstructor
public enum OrderTopics {
    FUNDS_FREEZE_REQUESTED(Names.FUNDS_FREEZE_REQUESTED, FundsFreezeRequestedEvent.class),
    ORDER_CANCEL_REQUESTED(Names.ORDER_CANCEL_REQUESTED, OrderCancelRequestedEvent.class),
    ORDER_CREATED(Names.ORDER_CREATED, OrderCreatedEvent.class);
    
    private final String topic;
    private final Class<?> eventType;
    
    public static final class Names {
        public static final String FUNDS_FREEZE_REQUESTED = "order.funds-freeze-requested";
        public static final String ORDER_CANCEL_REQUESTED = "order.cancel-requested";
        public static final String ORDER_CREATED = "order.created";
        
        private Names() {
        }
    }
}
