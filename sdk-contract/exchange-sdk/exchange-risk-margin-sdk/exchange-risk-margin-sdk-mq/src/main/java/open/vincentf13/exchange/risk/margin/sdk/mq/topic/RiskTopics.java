package open.vincentf13.exchange.risk.margin.sdk.mq.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.margin.sdk.mq.event.MarginPreCheckFailedEvent;
import open.vincentf13.exchange.risk.margin.sdk.mq.event.MarginPreCheckPassedEvent;

@Getter
@RequiredArgsConstructor
public enum RiskTopics {
    MARGIN_PRECHECK_PASSED(Names.MARGIN_PRECHECK_PASSED, MarginPreCheckPassedEvent.class),
    MARGIN_PRECHECK_FAILED(Names.MARGIN_PRECHECK_FAILED, MarginPreCheckFailedEvent.class);
    
    private final String topic;
    private final Class<?> eventType;
    
    public static final class Names {
        public static final String MARGIN_PRECHECK_PASSED = "risk.margin-check-passed";
        public static final String MARGIN_PRECHECK_FAILED = "risk.margin-check-failed";
        
        private Names() {
        }
    }
}
