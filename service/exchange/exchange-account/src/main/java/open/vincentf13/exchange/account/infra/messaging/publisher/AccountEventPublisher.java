package open.vincentf13.exchange.account.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.infra.AccountEvent;
import open.vincentf13.exchange.account.sdk.mq.event.AccountEntryCreatedEvent;
import open.vincentf13.exchange.account.sdk.mq.topic.AccountTopics;
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
public class AccountEventPublisher {
    
    private final MqOutboxRepository outboxRepository;
    
    public void publishAccountEntryCreated(Long entryId,
                                           Long userId,
                                           AssetSymbol asset,
                                           BigDecimal deltaBalance,
                                           BigDecimal balanceAfter,
                                           ReferenceType referenceType,
                                           String referenceId,
                                           EntryType entryType,
                                           Long instrumentId) {
        AccountEntryCreatedEvent event = new AccountEntryCreatedEvent(
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
        outboxRepository.append(AccountTopics.ACCOUNT_ENTRY_CREATED.getTopic(), entryId, event, null);
        OpenLog.info(AccountEvent.ACCOUNT_ENTRY_CREATED_ENQUEUED,
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
