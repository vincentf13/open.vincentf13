package open.vincentf13.exchange.matching.infra.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.matching.domain.instrument.Instrument;
import org.springframework.stereotype.Component;

/**
 * Cache for Instrument data.
 */
@Component
public class InstrumentCache {

    private static InstrumentCache instance;

    private final Map<Long, Instrument> cache = new ConcurrentHashMap<>();

    public InstrumentCache() {
        instance = this;
    }

    /**
     * Puts a list of instrument domain objects into the cache.
     *
     * @param instruments List of instrument domain objects
     */
    public void putAllDomain(List<Instrument> instruments) {
        if (instruments == null) {
            return;
        }

        instruments.forEach(instrument -> {
            cache.put(instrument.getInstrumentId(), instrument);
        });
    }

    /**
     * Puts a list of instrument summaries into the cache.
     * Converts DTOs to Domain entities.
     *
     * @param instrumentSummaries List of instrument summaries from Admin service
     */
    public void putAll(List<InstrumentSummaryResponse> instrumentSummaries) {
        if (instrumentSummaries == null) {
            return;
        }

        instrumentSummaries.forEach(dto -> {
            Instrument instrument = Instrument.from(dto);
            cache.put(instrument.getInstrumentId(), instrument);
        });
    }

    /**
     * Retrieves an instrument by its ID.
     *
     * @param instrumentId The instrument ID
     * @return The Instrument, or null if not found
     */
    public Instrument get(Long instrumentId) {
        return cache.get(instrumentId);
    }

    /**
     * Static accessor for legacy/domain objects that cannot use DI.
     * @param instrumentId The instrument ID
     * @return The Instrument, or null if not found
     */
    public static Instrument getInstrument(Long instrumentId) {
        if (instance == null) {
            return null;
        }
        return instance.get(instrumentId);
    }

    public int size() {
        return cache.size();
    }
}
