package open.vincentf13.exchange.account.ledger.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.infra.LedgerEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFrozenEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.LedgerEntryCreatedEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.topic.LedgerTopics;
import open.vincentf13.exchange.common.sdk.enums.EntryType;
import open.vincentf13.exchange.common.sdk.enums.ReferenceType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class LedgerEventPublisher {
    
    private final MqOutboxRepository outboxRepository;
    
    public void publishFundsFrozen(Long orderId,
                                   Long userId,
                                   AssetSymbol asset,
                                   BigDecimal frozenAmount) {
        FundsFrozenEvent event = new FundsFrozenEvent(orderId, userId, asset, frozenAmount);
        outboxRepository.append(LedgerTopics.FUNDS_FROZEN.getTopic(), orderId, event, null);
        OpenLog.info(LedgerEvent.FUNDS_FROZEN_ENQUEUED,
                     "orderId", orderId,
                     "userId", userId,
                     "asset", asset,
                     "amount", frozenAmount);
    }
    
    public void publishFundsFreezeFailed(Long orderId,
                                         String reason) {
        FundsFreezeFailedEvent event = new FundsFreezeFailedEvent(orderId, reason);
        outboxRepository.append(LedgerTopics.FUNDS_FREEZE_FAILED.getTopic(), orderId, event, null);
        OpenLog.warn(LedgerEvent.FUNDS_FREEZE_FAILED_ENQUEUED,
                     "orderId", orderId,
                     "reason", reason);
    }
    
    public void publishLedgerEntryCreated(Long entryId,
                                          Long userId,
                                          AssetSymbol asset,
                                          BigDecimal deltaBalance,
                                          BigDecimal balanceAfter,
                                          ReferenceType referenceType,
                                          String referenceId,
                                          EntryType entryType,
                                          Long instrumentId) {
        LedgerEntryCreatedEvent event = new LedgerEntryCreatedEvent(
                entryId,
                userId,
                asset,
                deltaBalance,
                balanceAfter,
                referenceType,
                referenceId,
                entryType,
                instrumentId,
                Instant.now()
        );
        outboxRepository.append(LedgerTopics.LEDGER_ENTRY_CREATED.getTopic(), entryId, event, null);
        OpenLog.info(LedgerEvent.LEDGER_ENTRY_CREATED_ENQUEUED,
                     "entryId", entryId,
                     "userId", userId,
                     "asset", asset,
                     "deltaBalance", deltaBalance,
                     "balanceAfter", balanceAfter,
                     "referenceType", referenceType,
                     "referenceId", referenceId,
                     "entryType", entryType,
                     "instrumentId", instrumentId,
                     "eventTime", event.eventTime()
                    );
    }
}
