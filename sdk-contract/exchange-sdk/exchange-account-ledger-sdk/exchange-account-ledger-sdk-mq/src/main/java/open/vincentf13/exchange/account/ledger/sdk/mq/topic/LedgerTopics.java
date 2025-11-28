package open.vincentf13.exchange.account.ledger.sdk.mq.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFrozenEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.LedgerEntryCreatedEvent;

@Getter
@RequiredArgsConstructor
public enum LedgerTopics {
    FUNDS_FROZEN(Names.FUNDS_FROZEN, FundsFrozenEvent.class),
    FUNDS_FREEZE_FAILED(Names.FUNDS_FREEZE_FAILED, FundsFreezeFailedEvent.class),
    LEDGER_ENTRY_CREATED(Names.LEDGER_ENTRY_CREATED, LedgerEntryCreatedEvent.class);
    
    private final String topic;
    private final Class<?> eventType;
    
    public static final class Names {
        public static final String FUNDS_FROZEN = "ledger.funds-frozen";
        public static final String FUNDS_FREEZE_FAILED = "ledger.funds-freeze-failed";
        public static final String LEDGER_ENTRY_CREATED = "ledger.entry-created";
        
        private Names() {
        }
    }
}
