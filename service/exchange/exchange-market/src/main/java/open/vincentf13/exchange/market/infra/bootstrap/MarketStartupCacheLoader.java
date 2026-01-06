package open.vincentf13.exchange.market.infra.bootstrap;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.market.infra.MarketEvent;
import open.vincentf13.exchange.market.infra.cache.InstrumentCache;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketStartupCacheLoader {

    private static final long RETRY_DELAY_MS = 3000;

    private final ExchangeAdminClient adminClient;
    private final InstrumentCache instrumentCache;

    public void loadCaches() {
        OpenLog.info(MarketEvent.STARTUP_CACHE_LOADING);

        int attempt = 0;
        while (true) {
            try {
                attempt++;
                OpenLog.info(MarketEvent.STARTUP_CACHE_LOADING, "attempt", attempt, "retryDelayMs", RETRY_DELAY_MS);

                loadInstruments();

                OpenLog.info(MarketEvent.STARTUP_CACHE_LOADED, "instruments", instrumentCache.size());
                return;
            } catch (Exception e) {
                OpenLog.error(MarketEvent.STARTUP_CACHE_LOAD_FAILED, e, "attempt", attempt, "retryDelayMs", RETRY_DELAY_MS);

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
        OpenLog.info(MarketEvent.STARTUP_LOADING_INSTRUMENTS);

        List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();

        instrumentCache.putAll(instruments);

        OpenLog.info(MarketEvent.STARTUP_INSTRUMENTS_LOADED, "count", instruments.size());
    }
}
