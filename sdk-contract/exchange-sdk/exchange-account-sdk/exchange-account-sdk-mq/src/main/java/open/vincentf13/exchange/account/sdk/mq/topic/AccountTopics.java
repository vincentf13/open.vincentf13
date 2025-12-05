package open.vincentf13.exchange.account.sdk.mq.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.mq.event.AccountEntryCreatedEvent;

@Getter
@RequiredArgsConstructor
public enum AccountTopics {
    ACCOUNT_ENTRY_CREATED(Names.ACCOUNT_ENTRY_CREATED, AccountEntryCreatedEvent.class);
    
    private final String topic;
    private final Class<?> eventType;
    
    public static final class Names {
        public static final String ACCOUNT_ENTRY_CREATED = "account.entry-created";
        
        private Names() {
        }
    }
}
