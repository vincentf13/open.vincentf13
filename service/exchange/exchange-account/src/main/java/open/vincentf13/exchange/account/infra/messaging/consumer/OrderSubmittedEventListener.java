package open.vincentf13.exchange.account.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.infra.AccountEvent;
import open.vincentf13.exchange.account.domain.service.AccountTransactionDomainService;
import open.vincentf13.exchange.order.mq.event.OrderSubmittedEvent;
import open.vincentf13.exchange.order.mq.topic.OrderTopics;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderSubmittedEventListener {

    private final AccountTransactionDomainService accountTransactionDomainService;

    @KafkaListener(topics = OrderTopics.Names.ORDER_SUBMITTED,
                   groupId = "${open.vincentf13.exchange.account.consumer-group:exchange-account}")
    public void onOrderSubmitted(@Payload OrderSubmittedEvent event,
                                 Acknowledgment acknowledgment) {
        try {
            OpenValidator.validateOrThrow(event);
            // TODO: implement funds freeze/validation per new account model
        } catch (Exception e) {
            OpenLog.warn(AccountEvent.ORDER_SUBMITTED_PAYLOAD_INVALID, e, "event", event);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
        acknowledgment.acknowledge();
    }
}
