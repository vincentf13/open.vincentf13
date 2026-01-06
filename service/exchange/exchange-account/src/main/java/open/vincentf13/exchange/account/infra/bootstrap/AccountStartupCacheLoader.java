package open.vincentf13.exchange.account.infra.bootstrap;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.infra.AccountEvent;
import open.vincentf13.exchange.account.infra.cache.InstrumentCache;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
  Service responsible for loading instrument configuration data into local caches at application startup for the Account service.
 */
@Service
@RequiredArgsConstructor
public class AccountStartupCacheLoader {

    private static final long RETRY_DELAY_MS = 3000;

    private final ExchangeAdminClient adminClient;
    private final InstrumentCache instrumentCache;
    
    public void loadCaches() {
        OpenLog.info(AccountEvent.STARTUP_CACHE_LOADING);

        int attempt = 0;
        while (true) {
            try {
                attempt++;
                OpenLog.info(AccountEvent.STARTUP_CACHE_LOADING, "attempt", attempt, "retryDelayMs", RETRY_DELAY_MS);

                loadInstruments();

                OpenLog.info(AccountEvent.STARTUP_CACHE_LOADED, "instruments", instrumentCache.size());
                return;

            } catch (Exception e) {
                OpenLog.error(AccountEvent.STARTUP_CACHE_LOAD_FAILED, e, "attempt", attempt, "retryDelayMs", RETRY_DELAY_MS);

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
      Loads all instruments from Admin service and stores them in the cache.
     */
    private void loadInstruments() {
        OpenLog.info(AccountEvent.STARTUP_LOADING_INSTRUMENTS);

        List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();

        instrumentCache.putAll(instruments);

        OpenLog.info(AccountEvent.STARTUP_INSTRUMENTS_LOADED, "count", instruments.size());
    }
}
