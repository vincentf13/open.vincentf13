package open.vincentf13.exchange.position.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRejectedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRequestedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReservedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionTopics;
import open.vincentf13.exchange.position.service.PositionCommandService;
import open.vincentf13.exchange.position.service.PositionCommandService.PositionReserveResult;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class PositionReserveRequestListener {

    private final PositionCommandService positionCommandService;
    private final PositionEventPublisher positionEventPublisher;

    @KafkaListener(
            topics = PositionTopics.POSITION_RESERVE_REQUESTED,
            groupId = "${exchange.position.reserve.consumer-group:exchange-position-reserve}"
    )
    public void handleReserveRequest(@Payload PositionReserveRequestedEvent event) {
        if (event == null) {
            return;
        }
        PositionReserveResult result = positionCommandService.reserveForClose(
                event.userId(),
                event.instrumentId(),
                event.quantity()
        );
        if (result.success()) {
            PositionReservedEvent reservedEvent = new PositionReservedEvent(
                    event.orderId(),
                    event.userId(),
                    event.instrumentId(),
                    event.intentType(),
                    result.reservedQuantity(),
                    Instant.now()
            );
            positionEventPublisher.publishReserved(reservedEvent);
            OpenLog.info(log, "PositionReserved", "Position reserved", "orderId", event.orderId());
        } else {
            PositionReserveRejectedEvent rejectedEvent = new PositionReserveRejectedEvent(
                    event.orderId(),
                    event.userId(),
                    event.instrumentId(),
                    event.intentType(),
                    result.reason(),
                    Instant.now()
            );
            positionEventPublisher.publishRejected(rejectedEvent);
            OpenLog.warn(log, "PositionReserveRejected", "Position reserve rejected", null,
                    "orderId", event.orderId(),
                    "reason", result.reason());
        }
    }
}
