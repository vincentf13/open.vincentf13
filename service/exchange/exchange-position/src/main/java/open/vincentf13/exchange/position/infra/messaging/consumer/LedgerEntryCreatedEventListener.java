package open.vincentf13.exchange.position.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.LedgerEntryCreatedEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.topic.LedgerTopics;
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
public class LedgerEntryCreatedEventListener {

    private final PositionCommandService positionCommandService;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(
            topics = LedgerTopics.Names.LEDGER_ENTRY_CREATED,
            groupId = "${exchange.position.ledger.consumer-group:exchange-position-ledger}"
    )
    public void onLedgerEntryCreated(@Payload LedgerEntryCreatedEvent event, Acknowledgment ack) {
        try {
            OpenValidator.validateOrThrow(event);
        } catch (Exception e) {
            OpenLog.warn(PositionLogEvent.POSITION_LEDGER_PAYLOAD_INVALID, e, "event", event);
            ack.acknowledge();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            positionCommandService.handleLedgerEntryCreated(event);
        });
        ack.acknowledge();
    }
}
