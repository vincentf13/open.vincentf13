package open.vincentf13.exchange.position.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.infra.PositionEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRequestedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionTopics;
import open.vincentf13.exchange.position.service.PositionReserveCommandService;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PositionReserveRequestedEventListener {

    private final PositionReserveCommandService positionReserveCommandService;

    @KafkaListener(topics = PositionTopics.Names.POSITION_RESERVE_REQUESTED,
                   groupId = "${exchange.position.reserve.consumer-group:exchange-position-reserve}")
    public void onPositionReserveRequested(@Payload PositionReserveRequestedEvent event,
                                           Acknowledgment acknowledgment) {
        try {
            positionReserveCommandService.handlePositionReserveRequested(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            OpenLog.error(PositionEvent.POSITION_RESERVE_REJECTED, e, "event", event);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }
}
