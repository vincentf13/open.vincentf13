package open.vincentf13.exchange.account.infra.bootstrap;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.account.infra.AccountEvent;
import open.vincentf13.exchange.account.infra.cache.InstrumentCache;
import open.vincentf13.exchange.account.infra.cache.RiskLimitCache;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskLimitResponse;
import open.vincentf13.exchange.risk.sdk.rest.client.ExchangeRiskClient;
import open.vincentf13.sdk.core.bootstrap.OpenStartupCacheLoader;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;

/**
 * Service responsible for loading instrument configuration data into local caches at application
 * startup for the Account service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountStartupCacheLoader extends OpenStartupCacheLoader {

  private final ExchangeAdminClient adminClient;
  private final ExchangeRiskClient riskClient;
  private final InstrumentCache instrumentCache;
  private final RiskLimitCache riskLimitCache;

  @Override
  protected void doLoadCaches() {
    loadInstruments();
    loadRiskLimits();
  }

  /** Loads all instruments from Admin service and stores them in the cache. */
  private void loadInstruments() {
    OpenLog.info(log, AccountEvent.STARTUP_LOADING_INSTRUMENTS);

    List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();

    instrumentCache.putAll(instruments);

    OpenLog.info(log, AccountEvent.STARTUP_INSTRUMENTS_LOADED, "count", instruments.size());
  }

  private void loadRiskLimits() {
    OpenLog.info(log, AccountEvent.STARTUP_LOADING_RISK_LIMITS);

    List<RiskLimitResponse> riskLimits = riskClient.list(null).data();

    if (riskLimits != null) {
      riskLimits.forEach(
          riskLimit -> riskLimitCache.put(riskLimit.instrumentId(), riskLimit));
      OpenLog.info(log, AccountEvent.STARTUP_RISK_LIMITS_LOADED, "count", riskLimits.size());
    }
  }
}
