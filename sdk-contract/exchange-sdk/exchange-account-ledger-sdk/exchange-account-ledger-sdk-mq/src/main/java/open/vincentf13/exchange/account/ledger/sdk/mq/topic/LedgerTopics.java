package open.vincentf13.exchange.account.ledger.sdk.mq.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFrozenEvent;

@Getter
@RequiredArgsConstructor
public enum LedgerTopics {
    FUNDS_FROZEN("ledger.funds-frozen", FundsFrozenEvent.class),
    FUNDS_FREEZE_FAILED("ledger.funds-freeze-failed", FundsFreezeFailedEvent.class);

    private final String topic;
    private final Class<?> eventType;
}
