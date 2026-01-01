package open.vincentf13.exchange.position.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import open.vincentf13.exchange.position.infra.PositionEvent;
import open.vincentf13.exchange.position.service.PositionTradeCloseService;
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
public class TradeExecutedEventListener implements ConsumerSeekAware {
    
    private final PositionTradeCloseService positionTradeCloseService;

    /**
     調試用
     * @param assignments
     * @param callback
     */
    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        callback.seekToBeginning(assignments.keySet());
    }
    
    @KafkaListener(topics = MatchingTopics.Names.TRADE_EXECUTED,
                   groupId = "${exchange.position.trade-executed.consumer-group:exchange-position-trade-executed}")
    public void onTradeExecuted(@Payload TradeExecutedEvent event,
                                Acknowledgment acknowledgment) {
        try {
            positionTradeCloseService.handleTradeExecuted(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            OpenLog.error(PositionEvent.POSITION_TRADE_PAYLOAD_INVALID, e, "event", event);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }
}
