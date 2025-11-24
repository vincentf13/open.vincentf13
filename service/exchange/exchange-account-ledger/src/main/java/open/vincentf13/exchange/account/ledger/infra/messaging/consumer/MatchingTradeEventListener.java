package open.vincentf13.exchange.account.ledger.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.account.ledger.service.LedgerBalanceCommandService;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchingTradeEventListener {

    private final LedgerBalanceCommandService ledgerBalanceCommandService;

    @KafkaListener(
            topics = MatchingTopics.Names.TRADE_EXECUTED,
            groupId = "${open.vincentf13.exchange.account.ledger.matching.consumer-group:exchange-account-ledger-trade}"
    )
    public void onTradeExecuted(@Payload TradeExecutedEvent event, Acknowledgment acknowledgment) {
        if (event == null) {
            log.warn("Skip TradeExecuted event due to missing payload");
            acknowledgment.acknowledge();
            return;
        }
        ledgerBalanceCommandService.handleTradeExecuted(event);
        acknowledgment.acknowledge();
    }
}
