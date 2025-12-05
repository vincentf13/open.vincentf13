package open.vincentf13.exchange.position.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.mq.event.AccountEntryCreatedEvent;
import open.vincentf13.exchange.account.sdk.mq.topic.AccountTopics;
import open.vincentf13.exchange.position.infra.PositionLogEvent;
import open.vincentf13.exchange.position.service.PositionCommandService;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class AccountEntryCreatedEventListener {

    private final PositionCommandService positionCommandService;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(
            topics = AccountTopics.Names.ACCOUNT_ENTRY_CREATED,
            groupId = "${exchange.position.account.consumer-group:exchange-position-account}"
    )
    public void onAccountEntryCreated(@Payload AccountEntryCreatedEvent event, Acknowledgment ack) {
        try {
            OpenValidator.validateOrThrow(event);
        } catch (Exception e) {
            OpenLog.warn(PositionLogEvent.POSITION_ACCOUNT_PAYLOAD_INVALID, e, "event", event);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
        transactionTemplate.executeWithoutResult(status -> {
            positionCommandService.handleAccountEntryCreated(event);
        });
        ack.acknowledge();
    }
}
