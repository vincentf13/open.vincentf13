package open.vincentf13.exchange.risk.infra.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.market.sdk.rest.client.ExchangeMarketClient;
import open.vincentf13.exchange.risk.infra.RiskEvent;
import open.vincentf13.exchange.risk.infra.cache.InstrumentCache;
import open.vincentf13.exchange.risk.infra.cache.MarkPriceCache;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StartupCacheLoader {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final ExchangeAdminClient adminClient;
    private final ExchangeMarketClient marketClient;
    private final InstrumentCache instrumentCache;
    private final MarkPriceCache markPriceCache;

    public void loadCaches() {
        OpenLog.info(RiskEvent.STARTUP_CACHE_LOADING);

        int attempt = 0;
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                attempt++;
                OpenLog.info(RiskEvent.STARTUP_CACHE_LOADING, "attempt", attempt, "maxAttempts", MAX_RETRY_ATTEMPTS);

                loadInstruments();
                loadMarkPrices();

                OpenLog.info(RiskEvent.STARTUP_CACHE_LOADED, "instruments", instrumentCache.size());
                return;

            } catch (Exception e) {
                OpenLog.error(RiskEvent.STARTUP_CACHE_LOAD_FAILED, e, "attempt", attempt, "maxAttempts", MAX_RETRY_ATTEMPTS);

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

    private void loadInstruments() {
        OpenLog.info(RiskEvent.STARTUP_LOADING_INSTRUMENTS);

        List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();

        instrumentCache.putAll(instruments);

        OpenLog.info(RiskEvent.STARTUP_INSTRUMENTS_LOADED, "count", instruments.size());
    }

    private void loadMarkPrices() {
        instrumentCache.getAll().forEach(instrument -> {
            try {
                var response = marketClient.getMarkPrice(instrument.instrumentId());
                if (response.isSuccess() && response.data() != null) {
                    markPriceCache.put(instrument.instrumentId(), response.data().getMarkPrice());
                }
            } catch (Exception e) {
                OpenLog.warn(RiskEvent.STARTUP_MARK_PRICE_LOAD_FAILED, e, "instrumentId", instrument.instrumentId());
            }
        });
    }
}
