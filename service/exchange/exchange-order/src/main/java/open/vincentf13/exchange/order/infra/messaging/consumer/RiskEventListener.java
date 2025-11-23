package open.vincentf13.exchange.order.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.margin.sdk.mq.event.MarginPreCheckFailedEvent;
import open.vincentf13.exchange.order.infra.messaging.handler.OrderFailureHandler;
import open.vincentf13.exchange.risk.margin.sdk.mq.topic.RiskTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RiskEventListener {

    private final OrderFailureHandler orderFailureHandler;

    @KafkaListener(
            topics = RiskTopics.Names.MARGIN_PRECHECK_FAILED,
            groupId = "${open.vincentf13.exchange.order.risk.consumer-group:exchange-order-risk}"
    )
    public void onMarginPreCheckFailed(@Payload MarginPreCheckFailedEvent event, Acknowledgment acknowledgment) {
        if (event == null || event.orderId() == null) {
            acknowledgment.acknowledge();
            return;
        }
        orderFailureHandler.markFailed(event.orderId(), "RISK_REJECT", event.reason());
        acknowledgment.acknowledge();
    }
}
