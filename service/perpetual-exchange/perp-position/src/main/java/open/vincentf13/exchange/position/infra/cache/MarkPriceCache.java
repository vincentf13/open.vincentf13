package open.vincentf13.exchange.position.infra.cache;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * In-memory cache for the latest mark price of each instrument. This cache is updated by consuming
 * {@link open.vincentf13.exchange.market.mq.event.MarkPriceUpdatedEvent}.
 */
@Component
public class MarkPriceCache {

  private final ConcurrentMap<Long, MarkPriceEntry> cache = new ConcurrentHashMap<>();

  public static BigDecimal resolveEffectiveMarkPrice(
      MarkPriceCache cache, Long instrumentId, BigDecimal fallbackPrice) {
    return cache
        .get(instrumentId)
        .filter(markPrice -> markPrice.compareTo(BigDecimal.ZERO) > 0)
        .orElse(fallbackPrice);
  }

  /**
   * Gets the latest mark price for a given instrument.
   *
   * @param instrumentId The ID of the instrument.
   * @return An Optional containing the latest mark price, or empty if not found.
   */
  public Optional<BigDecimal> get(Long instrumentId) {
    return Optional.ofNullable(cache.get(instrumentId)).map(MarkPriceEntry::markPrice);
  }

  /**
   * Updates the mark price for an instrument, ensuring that older prices do not overwrite newer
   * ones.
   *
   * @param instrumentId The ID of the instrument.
   * @param markPrice The new mark price.
   * @param eventTime The timestamp of the event that triggered the update.
   */
  public void update(Long instrumentId, BigDecimal markPrice, Instant eventTime) {
    cache.compute(
        instrumentId,
        (key, currentEntry) -> {
          // If there is no current entry, or if the new event is more recent, update the entry.
          if (currentEntry == null || eventTime.isAfter(currentEntry.eventTime())) {
            return new MarkPriceEntry(markPrice, eventTime);
          }
          // Otherwise, keep the existing entry.
          return currentEntry;
        });
  }

  /**
   * Returns the number of mark prices in the cache.
   *
   * @return The cache size.
   */
  public int size() {
    return cache.size();
  }

  /** An entry in the mark price cache, containing the price and the event time. */
  public record MarkPriceEntry(BigDecimal markPrice, Instant eventTime) {}
}
