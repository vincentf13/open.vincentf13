package open.vincentf13.exchange.position.infra.startup;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.position.infra.PositionLogEvent;
import open.vincentf13.exchange.position.infra.cache.InstrumentCache;
import open.vincentf13.exchange.position.infra.cache.RiskLimitCache;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.RiskLimitResponse;
import open.vincentf13.exchange.risk.margin.sdk.rest.client.ExchangeRiskMarginClient;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service responsible for loading instrument and risk limit data at application startup.
 */
@Service
@RequiredArgsConstructor
public class StartupCacheLoader {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final ExchangeAdminClient adminClient;
    private final ExchangeRiskMarginClient riskMarginClient;
    private final InstrumentCache instrumentCache;
    private final RiskLimitCache riskLimitCache;

    /**
     * Loads all instruments and their risk limits into local caches.
     *
     * @throws RuntimeException if loading fails after max retry attempts
     */
    public void loadCaches() {
        OpenLog.info(PositionLogEvent.STARTUP_CACHE_LOADING);

        int attempt = 0;
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                attempt++;
                OpenLog.info(PositionLogEvent.STARTUP_CACHE_LOADING, "attempt", attempt, "maxAttempts", MAX_RETRY_ATTEMPTS);

                loadInstruments();
                loadRiskLimits();

                OpenLog.info(PositionLogEvent.STARTUP_CACHE_LOADED, "instruments", instrumentCache.size(), "riskLimits", riskLimitCache.size());
                return;

            } catch (Exception e) {
                OpenLog.error(PositionLogEvent.STARTUP_CACHE_LOAD_FAILED, e, "attempt", attempt, "maxAttempts", MAX_RETRY_ATTEMPTS);

                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    throw new RuntimeException("Failed to load caches after " + MAX_RETRY_ATTEMPTS + " attempts", e);
                }

                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Cache loading interrupted", ie);
                }
            }
        }
    }

    /**
     * Loads all instruments from Admin service and stores them in the cache.
     */
    private void loadInstruments() {
        OpenLog.info(PositionLogEvent.STARTUP_LOADING_INSTRUMENTS);

        List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();

        instrumentCache.putAll(instruments);

        OpenLog.info(PositionLogEvent.STARTUP_INSTRUMENTS_LOADED, "count", instruments.size());
    }

    /**
     * Loads risk limits for all instruments from Risk service and stores them in the cache.
     */
    private void loadRiskLimits() {
        OpenLog.info(PositionLogEvent.STARTUP_LOADING_RISK_LIMITS, "instrumentCount", instrumentCache.size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        instrumentCache.getAll().forEach(instrument -> {
            Long instrumentId = instrument.instrumentId();
            try {
                RiskLimitResponse riskLimit = riskMarginClient.getRiskLimit(instrumentId).data();
                riskLimitCache.put(instrumentId, riskLimit);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                OpenLog.warn(PositionLogEvent.STARTUP_RISK_LIMIT_FETCH_FAILED, e, "instrumentId", instrumentId);
            }
        });

        OpenLog.info(PositionLogEvent.STARTUP_RISK_LIMITS_LOADED, "succeeded", successCount.get(), "failed", failureCount.get());

        if (failureCount.get() > 0) {
            OpenLog.warn(PositionLogEvent.STARTUP_RISK_LIMIT_LOAD_PARTIAL, "failed", failureCount.get(), "total", instrumentCache.size());
        }
    }
}
