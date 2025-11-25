package open.vincentf13.exchange.account.ledger.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFrozenEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.topic.LedgerTopics;
import open.vincentf13.exchange.sdk.common.enums.AssetSymbol;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.exchange.account.ledger.infra.LedgerEventEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class LedgerEventPublisher {

    private final MqOutboxRepository outboxRepository;

    public void publishFundsFrozen(Long orderId, Long userId, AssetSymbol asset, BigDecimal frozenAmount) {
        FundsFrozenEvent event = new FundsFrozenEvent(orderId, userId, asset.code(), frozenAmount);
        outboxRepository.append(LedgerTopics.FUNDS_FROZEN.getTopic(), orderId, event, null);
        OpenLog.info(LedgerEventEnum.FUNDS_FROZEN_ENQUEUED,
                "orderId", orderId,
                "userId", userId,
                "asset", asset,
                "amount", frozenAmount);
    }

    public void publishFundsFreezeFailed(Long orderId, String reason) {
        FundsFreezeFailedEvent event = new FundsFreezeFailedEvent(orderId, reason);
        outboxRepository.append(LedgerTopics.FUNDS_FREEZE_FAILED.getTopic(), orderId, event, null);
        OpenLog.warn(LedgerEventEnum.FUNDS_FREEZE_FAILED_ENQUEUED,
                "orderId", orderId,
                "reason", reason);
    }
}
