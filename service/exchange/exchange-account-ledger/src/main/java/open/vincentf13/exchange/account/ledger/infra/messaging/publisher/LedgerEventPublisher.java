package open.vincentf13.exchange.account.ledger.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFrozenEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.topic.LedgerTopics;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.AssetSymbol;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerEventPublisher {

    private final MqOutboxRepository outboxRepository;

    public void publishFundsFrozen(Long orderId, Long userId, AssetSymbol asset, BigDecimal frozenAmount) {
        FundsFrozenEvent event = new FundsFrozenEvent(orderId, userId, asset.code(), frozenAmount);
        outboxRepository.append(LedgerTopics.FUNDS_FROZEN, orderId, event, null);
        log.info("FundsFrozen event enqueued. orderId={} userId={} asset={} amount={}",
                orderId, userId, asset, frozenAmount);
    }

    public void publishFundsFreezeFailed(Long orderId, String reason) {
        FundsFreezeFailedEvent event = new FundsFreezeFailedEvent(orderId, reason);
        outboxRepository.append(LedgerTopics.FUNDS_FREEZE_FAILED, orderId, event, null);
        log.warn("FundsFreezeFailed event enqueued. orderId={} reason={} ", orderId, reason);
    }
}
