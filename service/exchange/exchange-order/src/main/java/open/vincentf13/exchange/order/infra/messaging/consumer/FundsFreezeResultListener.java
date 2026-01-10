package open.vincentf13.exchange.order.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.mq.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.account.sdk.mq.event.FundsFrozenEvent;
import open.vincentf13.exchange.account.sdk.mq.topic.AccountFundsTopics;
import open.vincentf13.exchange.order.service.OrderCommandService;
import open.vincentf13.sdk.core.validator.OpenValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FundsFreezeResultListener {

    private final OrderCommandService orderCommandService;

    @KafkaListener(topics = AccountFundsTopics.Names.FUNDS_FROZEN,
                   groupId = "${open.vincentf13.exchange.order.consumer-group:exchange-order}")
    public void onFundsFrozen(@Payload FundsFrozenEvent event,
                              Acknowledgment acknowledgment) {
        OpenValidator.validateOrThrow(event);
        orderCommandService.processFundsFrozen(event.orderId(), event.eventTime());
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = AccountFundsTopics.Names.FUNDS_FREEZE_FAILED,
                   groupId = "${open.vincentf13.exchange.order.consumer-group:exchange-order}")
    public void onFundsFreezeFailed(@Payload FundsFreezeFailedEvent event,
                                    Acknowledgment acknowledgment) {
        OpenValidator.validateOrThrow(event);
        orderCommandService.processFundsFreezeFailed(event.orderId(),
                                                     event.reason(),
                                                     event.eventTime());
        acknowledgment.acknowledge();
    }
    
}
