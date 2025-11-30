package open.vincentf13.exchange.position.infra.cache;

import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory cache for instrument configuration data.
 * This cache is populated at application startup by fetching all instruments from the Admin service.
 */
@Component
public class InstrumentCache {

    private final ConcurrentMap<Long, InstrumentSummaryResponse> cache = new ConcurrentHashMap<>();

    /**
     * Gets the instrument configuration for a given instrument ID.
     *
     * @param instrumentId The ID of the instrument.
     * @return An Optional containing the instrument configuration, or empty if not found.
     */
    public Optional<InstrumentSummaryResponse> get(Long instrumentId) {
        return Optional.ofNullable(cache.get(instrumentId));
    }

    /**
     * Puts an instrument configuration into the cache.
     *
     * @param instrumentId The ID of the instrument.
     * @param instrument   The instrument configuration.
     */
    public void put(Long instrumentId, InstrumentSummaryResponse instrument) {
        cache.put(instrumentId, instrument);
    }

    /**
     * Puts all instrument configurations into the cache.
     *
     * @param instruments Collection of instrument configurations.
     */
    public void putAll(Collection<InstrumentSummaryResponse> instruments) {
        instruments.forEach(instrument -> cache.put(instrument.instrumentId(), instrument));
    }

    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Returns the number of instruments in the cache.
     *
     * @return The cache size.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Returns all instruments in the cache.
     *
     * @return Collection of all instruments.
     */
    public Collection<InstrumentSummaryResponse> getAll() {
        return cache.values();
    }
}
