package open.vincentf13.exchange.account.sdk.mq.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.mq.event.TradeMarginSettledEvent;

@Getter
@RequiredArgsConstructor
public enum AccountTradeTopics {
    TRADE_MARGIN_SETTLED(Names.TRADE_MARGIN_SETTLED, TradeMarginSettledEvent.class);

    private final String topic;
    private final Class<?> eventType;

    public static final class Names {
        public static final String TRADE_MARGIN_SETTLED = "account.trade-margin-settled";

        private Names() {
        }
    }
}
