package open.vincentf13.exchange.order.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.infra.messaging.event.MarginPreCheckFailedEvent;
import open.vincentf13.exchange.order.infra.messaging.handler.OrderFailureHandler;
import open.vincentf13.exchange.order.infra.messaging.topic.RiskTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RiskEventListener {

    private final OrderFailureHandler orderFailureHandler;

    @KafkaListener(
            topics = RiskTopics.MARGIN_PRECHECK_FAILED,
            groupId = "${open.vincentf13.exchange.order.risk.consumer-group:exchange-order-risk}"
    )
    public void onMarginPreCheckFailed(@Payload MarginPreCheckFailedEvent event, Acknowledgment acknowledgment) {
        try {
            if (event == null || event.orderId() == null) {
                return;
            }
            orderFailureHandler.markFailed(event.orderId(), "RISK_REJECT", event.reason());
        } finally {
            acknowledgment.acknowledge();
        }
    }
}
