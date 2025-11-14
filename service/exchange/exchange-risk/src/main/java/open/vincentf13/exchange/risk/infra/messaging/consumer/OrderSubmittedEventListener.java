package open.vincentf13.exchange.risk.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.mq.event.OrderSubmittedEvent;
import open.vincentf13.exchange.order.mq.topic.OrderTopics;
import open.vincentf13.exchange.risk.application.OrderPreCheckService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSubmittedEventListener {

    private final OrderPreCheckService orderPreCheckService;

    @KafkaListener(
            topics = OrderTopics.ORDER_SUBMITTED,
            groupId = "${exchange.risk.precheck.consumer-group:exchange-risk-precheck}"
    )
    public void onOrderSubmitted(@Payload OrderSubmittedEvent event, Acknowledgment acknowledgment) {
        boolean processed = false;
        try {
            processed = orderPreCheckService.handle(event);
        } catch (Exception ex) {
            log.error("Failed to process OrderSubmitted event. orderId={} reason={}",
                    event != null ? event.orderId() : null, ex.getMessage(), ex);
        }
        if (processed) {
            acknowledgment.acknowledge();
        }
    }
}
