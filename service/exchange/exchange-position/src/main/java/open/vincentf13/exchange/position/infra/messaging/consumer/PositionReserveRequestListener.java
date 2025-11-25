package open.vincentf13.exchange.position.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.mq.topic.OrderTopics;
import open.vincentf13.exchange.position.infra.messaging.publisher.PositionEventPublisher;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRejectedEvent;
import open.vincentf13.exchange.order.mq.event.PositionReserveRequestedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReservedEvent;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionSide;
import open.vincentf13.exchange.position.service.PositionCommandService;
import open.vincentf13.exchange.position.service.PositionCommandService.PositionReserveOutcome;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.exchange.position.infra.PositionEventEnum;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class PositionReserveRequestListener {

    private final PositionCommandService positionCommandService;
    private final PositionEventPublisher positionEventPublisher;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(
            topics = OrderTopics.Names.POSITION_RESERVE_REQUESTED,
            groupId = "${exchange.position.reserve.consumer-group:exchange-position-reserve}"
    )
    public void handleReserveRequest(@Payload PositionReserveRequestedEvent event, Acknowledgment acknowledgment) {
        if (event == null) {
            acknowledgment.acknowledge();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> handleReserveRequestInTransaction(event));
        acknowledgment.acknowledge();
    }

    private void handleReserveRequestInTransaction(PositionReserveRequestedEvent event) {
        PositionReserveOutcome outcome = positionCommandService.reserveForClose(
                event.orderId(),
                event.userId(),
                event.instrumentId(),
                event.quantity(),
                toPositionSide(event.orderSide())
        );
        if (outcome.result().success()) {
            PositionReservedEvent reservedEvent = new PositionReservedEvent(
                    event.orderId(),
                    event.userId(),
                    event.instrumentId(),
                    event.intentType(),
                    outcome.result().reservedQuantity(),
                    outcome.avgOpenPrice(),
                    Instant.now()
            );
            positionEventPublisher.publishReserved(reservedEvent);
            OpenLog.info( PositionEventEnum.POSITION_RESERVED, "orderId", event.orderId());
            return;
        }
        PositionReserveRejectedEvent rejectedEvent = new PositionReserveRejectedEvent(
                    event.orderId(),
                    event.userId(),
                    event.instrumentId(),
                    event.intentType(),
                    outcome.result().reason(),
                    Instant.now()
        );
        positionEventPublisher.publishRejected(rejectedEvent);
        OpenLog.warn( PositionEventEnum.POSITION_RESERVE_REJECTED, ex,
                "orderId", event.orderId(),
                "reason", outcome.result().reason());
    }

    private PositionSide toPositionSide(open.vincentf13.exchange.order.sdk.rest.api.enums.OrderSide orderSide) {
        if (orderSide == null) {
            return null;
        }
        return orderSide == open.vincentf13.exchange.order.sdk.rest.api.enums.OrderSide.BUY
                ? PositionSide.LONG
                : PositionSide.SHORT;
    }
}
