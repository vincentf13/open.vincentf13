package open.vincentf13.exchange.account.ledger.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.service.LedgerBalanceCommandService;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.exchange.account.ledger.infra.LedgerEventEnum;
import open.vincentf13.exchange.risk.margin.sdk.mq.event.MarginPreCheckPassedEvent;
import open.vincentf13.exchange.risk.margin.sdk.mq.topic.RiskTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RiskMarginEventListener {

    private final LedgerBalanceCommandService ledgerBalanceCommandService;

    @KafkaListener(
            topics = RiskTopics.Names.MARGIN_PRECHECK_PASSED,
            groupId = "${open.vincentf13.exchange.account.ledger.risk.consumer-group:exchange-account-ledger-risk}"
    )
    public void onMarginPreCheckPassed(@Payload MarginPreCheckPassedEvent event, Acknowledgment acknowledgment) {
        if (event == null) {
            OpenLog.warn(LedgerEventEnum.RISK_MARGIN_IDENTIFIERS_MISSING, "event", event);
            acknowledgment.acknowledge();
            return;
        }
        ledgerBalanceCommandService.handleMarginPreCheckPassed(event);
        acknowledgment.acknowledge();
    }
}
