package open.vincentf13.exchange.account.ledger.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.infra.LedgerEvent;
import open.vincentf13.exchange.account.ledger.service.LedgerBalanceCommandService;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchingTradeEventListener {
    
    private final LedgerBalanceCommandService ledgerBalanceCommandService;
    
    @KafkaListener(topics = MatchingTopics.Names.TRADE_EXECUTED,
                   groupId = "${open.vincentf13.exchange.account-ledger.consumer-group:exchange-account-ledger}")
    public void onTradeExecuted(@Payload TradeExecutedEvent event,
                                Acknowledgment acknowledgment) {
        try {
            OpenValidator.validateOrThrow(event);
        } catch (Exception e) {
            OpenLog.warn(LedgerEvent.MATCHING_TRADE_PAYLOAD_MISSING, e, "event", event);
            acknowledgment.acknowledge();
            return;
        }
        ledgerBalanceCommandService.handleTradeExecuted(event);
        acknowledgment.acknowledge();
    }
}
