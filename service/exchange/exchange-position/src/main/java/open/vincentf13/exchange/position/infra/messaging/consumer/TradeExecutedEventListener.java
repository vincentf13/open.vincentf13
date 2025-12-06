package open.vincentf13.exchange.position.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import open.vincentf13.exchange.position.infra.PositionEvent;
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
public class TradeExecutedEventListener {

    private final PositionCommandService positionCommandService;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(
            topics = MatchingTopics.Names.TRADE_EXECUTED,
            groupId = "${exchange.position.trade.consumer-group:exchange-position-trade}"
    )
    public void onTradeExecuted(@Payload TradeExecutedEvent event, Acknowledgment ack) {
        try {
            OpenValidator.validateOrThrow(event);
        } catch (Exception e) {
            OpenLog.warn(PositionEvent.POSITION_TRADE_PAYLOAD_INVALID, e, "event", event);
            ack.acknowledge();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            positionCommandService.handleTradeExecuted(event);
        });
        ack.acknowledge();
    }
}
