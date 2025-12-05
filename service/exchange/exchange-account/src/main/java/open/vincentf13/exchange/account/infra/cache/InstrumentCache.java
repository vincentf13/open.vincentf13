package open.vincentf13.exchange.account.infra.cache;

import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
  In-memory cache for instrument configuration data, populated at startup from Admin service.
 */
@Component
public class InstrumentCache {

    private final ConcurrentMap<Long, InstrumentSummaryResponse> cache = new ConcurrentHashMap<>();

    public Optional<InstrumentSummaryResponse> get(Long instrumentId) {
        return Optional.ofNullable(cache.get(instrumentId));
    }

    public void put(Long instrumentId, InstrumentSummaryResponse instrument) {
        cache.put(instrumentId, instrument);
    }

    public void putAll(Collection<InstrumentSummaryResponse> instruments) {
        instruments.forEach(instrument -> cache.put(instrument.instrumentId(), instrument));
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    public Collection<InstrumentSummaryResponse> getAll() {
        return cache.values();
    }
}
