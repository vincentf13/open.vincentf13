package open.vincentf13.exchange.account.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.service.AccountCommandService;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import open.vincentf13.sdk.core.validator.OpenValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchingTradeEventListener {
    
    private final AccountCommandService accountCommandService;
    
    @KafkaListener(topics = MatchingTopics.Names.TRADE_EXECUTED,
                   groupId = "${open.vincentf13.exchange.account.consumer-group:exchange-account}")
    public void onTradeExecuted(@Payload TradeExecutedEvent event,
                                Acknowledgment acknowledgment) {
        OpenValidator.validateOrThrow(event);
        accountCommandService.handleTradeExecuted(event);
        acknowledgment.acknowledge();
    }
}
