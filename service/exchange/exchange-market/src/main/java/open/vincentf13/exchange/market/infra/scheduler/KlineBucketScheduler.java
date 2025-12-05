package open.vincentf13.exchange.market.infra.scheduler;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.infra.MarketEvent;
import open.vincentf13.exchange.market.infra.cache.KlineAggregationService;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class KlineBucketScheduler {
    
    private final KlineAggregationService klineAggregationService;
    
    /**
     Scan every minute to close buckets whose window already ended.
     */
    @Scheduled(cron = "0 * * * * *")
    public void closeExpiredBuckets() {
        Instant now = Instant.now();
        klineAggregationService.closeExpiredBuckets(now);
        OpenLog.debug(MarketEvent.KLINE_CLEANUP_TRIGGERED, "timestamp", now);
    }
}
