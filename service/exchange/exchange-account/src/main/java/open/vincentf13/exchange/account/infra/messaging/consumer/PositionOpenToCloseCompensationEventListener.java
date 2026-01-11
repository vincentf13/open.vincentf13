package open.vincentf13.exchange.account.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.infra.AccountEvent;
import open.vincentf13.exchange.account.service.AccountCommandService;
import open.vincentf13.exchange.position.sdk.mq.event.PositionOpenToCloseCompensationEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionTopics;
import open.vincentf13.sdk.core.validator.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PositionOpenToCloseCompensationEventListener {

    private final AccountCommandService accountCommandService;

    @KafkaListener(topics = PositionTopics.Names.POSITION_OPEN_TO_CLOSE_COMPENSATION,
                   groupId = "${open.vincentf13.exchange.account.consumer-group:exchange-account}")
    public void onOpenToCloseCompensation(@Payload PositionOpenToCloseCompensationEvent event,
                                          Acknowledgment acknowledgment) {
        try {
            OpenValidator.validateOrThrow(event);
            accountCommandService.handleOpenToCloseCompensation(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            OpenLog.warn(AccountEvent.POSITION_OPEN_TO_CLOSE_COMPENSATION_PAYLOAD_INVALID, e, "event", event);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }
}
