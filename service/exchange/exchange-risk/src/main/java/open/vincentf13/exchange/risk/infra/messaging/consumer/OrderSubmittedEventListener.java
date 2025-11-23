package open.vincentf13.exchange.risk.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.mq.event.OrderSubmittedEvent;
import open.vincentf13.exchange.order.mq.topic.OrderTopics;
import open.vincentf13.exchange.risk.service.OrderQueryService;
import open.vincentf13.sdk.core.OpenValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSubmittedEventListener {

    private final OrderQueryService orderQueryService;

    @KafkaListener(
            topics = OrderTopics.Names.ORDER_SUBMITTED,
            groupId = "${exchange.risk.precheck.consumer-group:exchange-risk-precheck}"
    )
    public void onOrderSubmitted(@Payload OrderSubmittedEvent event, Acknowledgment acknowledgment) {
        if (event == null) {
            acknowledgment.acknowledge();
            return;
        }
        OpenValidator.validateOrThrow(event);
        orderQueryService.preCheck(event);
        acknowledgment.acknowledge();
    }
}
