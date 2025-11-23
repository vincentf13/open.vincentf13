package open.vincentf13.exchange.account.ledger.sdk.mq.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFrozenEvent;

@Getter
@RequiredArgsConstructor
public enum LedgerTopics {
    FUNDS_FROZEN(Names.FUNDS_FROZEN, FundsFrozenEvent.class),
    FUNDS_FREEZE_FAILED(Names.FUNDS_FREEZE_FAILED, FundsFreezeFailedEvent.class);

    private final String topic;
    private final Class<?> eventType;

    public static final class Names {
        public static final String FUNDS_FROZEN = "ledger.funds-frozen";
        public static final String FUNDS_FREEZE_FAILED = "ledger.funds-freeze-failed";

        private Names() {
        }
    }
}
