package open.vincentf13.exchange.account.infra.bootstrap;

import java.util.List;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.infra.AccountEvent;
import open.vincentf13.exchange.account.infra.cache.InstrumentCache;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.sdk.core.bootstrap.OpenStartupCacheLoader;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;

/**
 * Service responsible for loading instrument configuration data into local caches at application
 * startup for the Account service.
 */
@Service
@RequiredArgsConstructor
public class AccountStartupCacheLoader extends OpenStartupCacheLoader {

  private final ExchangeAdminClient adminClient;
  private final InstrumentCache instrumentCache;

  @Override
  protected void doLoadCaches() {
    loadInstruments();
  }

  /** Loads all instruments from Admin service and stores them in the cache. */
  private void loadInstruments() {
    OpenLog.info(AccountEvent.STARTUP_LOADING_INSTRUMENTS);

    List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();

    instrumentCache.putAll(instruments);

    OpenLog.info(AccountEvent.STARTUP_INSTRUMENTS_LOADED, "count", instruments.size());
  }
}
