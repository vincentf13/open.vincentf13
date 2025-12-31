package open.vincentf13.exchange.matching.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.domain.order.book.Order;
import open.vincentf13.exchange.matching.service.MatchingEngine;
import open.vincentf13.exchange.order.mq.event.OrderCreatedEvent;
import open.vincentf13.exchange.order.mq.topic.OrderTopics;
import open.vincentf13.sdk.core.OpenValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderCreatedEventListener {
    
    private final MatchingEngine matchingEngine;
   
    @KafkaListener(topics = OrderTopics.Names.ORDER_CREATED,
                   groupId = "${open.vincentf13.exchange.matching.consumer-group:exchange-matching}",
                   containerFactory = "kafkaBatchListenerContainerFactory",
                   // 調試用
                   properties = "auto.offset.reset=earliest",
                   concurrency = "1")
    public void onOrderCreated(@Payload List<OrderCreatedEvent> events,
                               Acknowledgment acknowledgment) {
        if (events == null || events.isEmpty()) {
            acknowledgment.acknowledge();
            return;
        }
        List<Order> orders = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            OrderCreatedEvent event = events.get(i);
            OpenValidator.validateOrThrow(event);
            Order order = Order.builder()
                               .orderId(event.orderId())
                               .userId(event.userId())
                               .instrumentId(event.instrumentId())
                               .clientOrderId(event.clientOrderId())
                               .side(event.side())
                               .type(event.type())
                               .intent(event.intent())
                               .tradeType(event.tradeType())
                               .price(event.price())
                               .quantity(event.quantity())
                               .submittedAt(event.submittedAt())
                               .build();
            orders.add(order);
        }
        matchingEngine.processBatch(orders);
        acknowledgment.acknowledge();
    }
}
