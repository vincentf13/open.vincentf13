package open.vincentf13.exchange.matching.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.domain.order.book.Order;
import open.vincentf13.exchange.matching.service.MatchingEngine;
import open.vincentf13.exchange.order.mq.event.OrderCreatedEvent;
import open.vincentf13.exchange.order.mq.topic.OrderTopics;
import open.vincentf13.sdk.core.OpenValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCreatedEventListener {
    
    private final MatchingEngine matchingEngine;
    
    @KafkaListener(topics = OrderTopics.Names.ORDER_CREATED,
                   groupId = "${open.vincentf13.exchange.matching.consumer-group:exchange-matching}",
                   concurrency = "1")
    public void onOrderCreated(@Payload OrderCreatedEvent event,
                               @Header(KafkaHeaders.OFFSET) long offset,
                               Acknowledgment acknowledgment) {
        OpenValidator.validateOrThrow(event);
        Order order = Order.builder()
                           .orderId(event.orderId())
                           .userId(event.userId())
                           .instrumentId(event.instrumentId())
                           .clientOrderId(event.clientOrderId())
                           .side(event.side())
                           .type(event.type())
                           .intent( event.intent())
                           .price(event.price())
                           .quantity(event.quantity())
                           .submittedAt( event.submittedAt())
                           .build();
        matchingEngine.process(order, offset);
        acknowledgment.acknowledge();
    }
}
