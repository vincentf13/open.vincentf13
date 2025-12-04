package open.vincentf13.exchange.account.ledger.sdk.mq.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.LedgerEntryCreatedEvent;

@Getter
@RequiredArgsConstructor
public enum LedgerTopics {
    LEDGER_ENTRY_CREATED(Names.LEDGER_ENTRY_CREATED, LedgerEntryCreatedEvent.class);
    
    private final String topic;
    private final Class<?> eventType;
    
    public static final class Names {
        public static final String LEDGER_ENTRY_CREATED = "ledger.entry-created";
        
        private Names() {
        }
    }
}
