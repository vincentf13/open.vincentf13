package open.vincentf13.exchange.account.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.infra.AccountEvent;
import open.vincentf13.exchange.account.service.AccountCommandService;
import open.vincentf13.exchange.position.sdk.mq.event.PositionMarginReleasedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionTopics;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PositionMarginReleasedEventListener implements ConsumerSeekAware {

    private final AccountCommandService accountCommandService;

    /**
     調試用
     * @param assignments
     * @param callback
     */
    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        callback.seekToBeginning(assignments.keySet());
    }

    @KafkaListener(topics = PositionTopics.Names.POSITION_MARGIN_RELEASED,
                   groupId = "${open.vincentf13.exchange.account.consumer-group:exchange-account}")
    public void onPositionMarginReleased(@Payload PositionMarginReleasedEvent event,
                                         Acknowledgment acknowledgment) {
        try {
            OpenValidator.validateOrThrow(event);
            accountCommandService.handlePositionMarginReleased(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            OpenLog.warn(AccountEvent.MATCHING_TRADE_PAYLOAD_MISSING, e, "event", event);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }
}
