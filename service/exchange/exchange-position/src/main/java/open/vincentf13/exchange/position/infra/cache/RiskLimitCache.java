package open.vincentf13.exchange.position.infra.cache;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskLimitResponse;
import org.springframework.stereotype.Component;

/**
 * In-memory cache for risk limit configuration data. This cache is populated at application startup
 * by fetching risk limits for all instruments from the Risk service.
 */
@Component
public class RiskLimitCache {

  private static final BigDecimal MAINTENANCE_MARGIN_RATE_DEFAULT = BigDecimal.valueOf(0.005);

  private final ConcurrentMap<Long, RiskLimitResponse> cache = new ConcurrentHashMap<>();

  public static BigDecimal resolveMaintenanceMarginRate(RiskLimitCache cache, Long instrumentId) {
    return cache
        .get(instrumentId)
        .map(
            riskLimit ->
                riskLimit.maintenanceMarginRate() != null
                    ? riskLimit.maintenanceMarginRate()
                    : MAINTENANCE_MARGIN_RATE_DEFAULT)
        .orElse(MAINTENANCE_MARGIN_RATE_DEFAULT);
  }

  /**
   * Gets the risk limit configuration for a given instrument ID.
   *
   * @param instrumentId The ID of the instrument.
   * @return An Optional containing the risk limit configuration, or empty if not found.
   */
  public Optional<RiskLimitResponse> get(Long instrumentId) {
    return Optional.ofNullable(cache.get(instrumentId));
  }

  /**
   * Puts a risk limit configuration into the cache.
   *
   * @param instrumentId The ID of the instrument.
   * @param riskLimit The risk limit configuration.
   */
  public void put(Long instrumentId, RiskLimitResponse riskLimit) {
    cache.put(instrumentId, riskLimit);
  }

  /** Clears all entries from the cache. */
  public void clear() {
    cache.clear();
  }

  /**
   * Returns the number of risk limits in the cache.
   *
   * @return The cache size.
   */
  public int size() {
    return cache.size();
  }
}
