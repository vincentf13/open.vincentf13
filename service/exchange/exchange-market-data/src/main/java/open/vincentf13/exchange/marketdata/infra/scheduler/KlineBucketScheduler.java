package open.vincentf13.exchange.marketdata.infra.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.marketdata.infra.cache.KlineAggregationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class KlineBucketScheduler {

    private final KlineAggregationService klineAggregationService;

    /**
     * Scan every minute to close buckets whose window already ended.
     */
    @Scheduled(cron = "0 * * * * *")
    public void closeExpiredBuckets() {
        Instant now = Instant.now();
        klineAggregationService.closeExpiredBuckets(now);
        log.debug("Kline bucket cleanup triggered at {}", now);
    }
}
