package open.vincentf13.exchange.position.infra.bootstrap;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.market.sdk.rest.client.ExchangeMarketClient;
import open.vincentf13.exchange.position.infra.PositionEvent;
import open.vincentf13.exchange.position.infra.cache.InstrumentCache;
import open.vincentf13.exchange.position.infra.cache.MarkPriceCache;
import open.vincentf13.exchange.position.infra.cache.RiskLimitCache;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskLimitResponse;
import open.vincentf13.exchange.risk.sdk.rest.client.ExchangeRiskClient;
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

    private static final long RETRY_DELAY_MS = 3000;

    private final ExchangeAdminClient adminClient;
    private final ExchangeRiskClient riskClient;
    private final ExchangeMarketClient marketClient;
    private final InstrumentCache instrumentCache;
    private final MarkPriceCache markPriceCache;
    private final RiskLimitCache riskLimitCache;

    /**
     * Loads all instruments and their risk limits into local caches.
     *
     * @throws RuntimeException if loading fails after max retry attempts
     */
    public void loadCaches() {
        OpenLog.info(PositionEvent.STARTUP_CACHE_LOADING);

        int attempt = 0;
        while (true) {
            try {
                attempt++;
                OpenLog.info(PositionEvent.STARTUP_CACHE_LOADING, "attempt", attempt, "retryDelayMs", RETRY_DELAY_MS);

                loadInstruments();
                loadRiskLimits();
                loadMarkPrices();

                OpenLog.info(PositionEvent.STARTUP_CACHE_LOADED, "instruments", instrumentCache.size(), "riskLimits", riskLimitCache.size(), "markPrices", markPriceCache.size());
                return;

            } catch (Exception e) {
                OpenLog.error(PositionEvent.STARTUP_CACHE_LOAD_FAILED, e, "attempt", attempt, "retryDelayMs", RETRY_DELAY_MS);

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
        OpenLog.info(PositionEvent.STARTUP_LOADING_INSTRUMENTS);

        List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();

        instrumentCache.putAll(instruments);

        OpenLog.info(PositionEvent.STARTUP_INSTRUMENTS_LOADED, "count", instruments.size());
    }

    /**
     * Loads risk limits for all instruments from Risk service and stores them in the cache.
     */
    private void loadRiskLimits() {
        OpenLog.info(PositionEvent.STARTUP_LOADING_RISK_LIMITS, "instrumentCount", instrumentCache.size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        instrumentCache.getAll().forEach(instrument -> {
            Long instrumentId = instrument.instrumentId();
            try {
                RiskLimitResponse riskLimit = riskClient.getRiskLimit(instrumentId).data();
                riskLimitCache.put(instrumentId, riskLimit);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                OpenLog.warn(PositionEvent.STARTUP_RISK_LIMIT_FETCH_FAILED, e, "instrumentId", instrumentId);
            }
        });

        OpenLog.info(PositionEvent.STARTUP_RISK_LIMITS_LOADED, "succeeded", successCount.get(), "failed", failureCount.get());
    }

    private void loadMarkPrices() {
        OpenLog.info(PositionEvent.STARTUP_CACHE_LOADING, "Loading mark prices");
        instrumentCache.getAll().forEach(instrument -> {
            Long instrumentId = instrument.instrumentId();
            try {
                var response = marketClient.getMarkPrice(instrumentId);
                if (response.isSuccess() && response.data() != null) {
                    markPriceCache.update(instrumentId, response.data().getMarkPrice(), response.data().getCalculatedAt());
                }
            } catch (Exception e) {
                OpenLog.warn(PositionEvent.STARTUP_CACHE_LOAD_PARTIAL, e, "instrumentId", instrumentId);
            }
        });
    }
}
