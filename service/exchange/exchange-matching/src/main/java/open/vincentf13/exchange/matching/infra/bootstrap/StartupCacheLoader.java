package open.vincentf13.exchange.matching.infra.bootstrap;

import java.util.List;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.exchange.matching.infra.cache.InstrumentCache;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StartupCacheLoader {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final ExchangeAdminClient adminClient;
    private final InstrumentCache instrumentCache;

    public void loadCaches() {
        OpenLog.info(MatchingEvent.STARTUP_CACHE_LOADING);

        int attempt = 0;
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                attempt++;
                OpenLog.info(MatchingEvent.STARTUP_CACHE_LOADING, "attempt", attempt, "maxAttempts", MAX_RETRY_ATTEMPTS);

                loadInstruments();

                OpenLog.info(MatchingEvent.STARTUP_CACHE_LOADED, "instruments", instrumentCache.size());
                return;

            } catch (Exception e) {
                OpenLog.error(MatchingEvent.STARTUP_CACHE_LOAD_FAILED, e, "attempt", attempt, "maxAttempts", MAX_RETRY_ATTEMPTS);

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
        OpenLog.info(MatchingEvent.STARTUP_LOADING_INSTRUMENTS);

        List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();
        if (instruments == null || instruments.isEmpty()) {
            OpenLog.warn(MatchingEvent.STARTUP_CACHE_LOAD_PARTIAL, "message", "no instruments returned");
            return;
        }

        instrumentCache.putAll(instruments);
        OpenLog.info(MatchingEvent.STARTUP_INSTRUMENTS_LOADED, "count", instrumentCache.size());
    }
}
