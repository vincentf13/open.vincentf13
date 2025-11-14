package open.vincentf13.exchange.risk.infra.cache;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.domain.model.MarkPriceSnapshot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class MarkPriceCache {

    private final MarkPriceCacheProperties properties;
    private final Map<Long, CachedEntry> cache = new ConcurrentHashMap<>();

    public void put(MarkPriceSnapshot snapshot) {
        if (snapshot == null || snapshot.getInstrumentId() == null) {
            return;
        }
        cache.put(snapshot.getInstrumentId(), new CachedEntry(snapshot, Instant.now()));
    }

    public Optional<MarkPriceSnapshot> get(Long instrumentId) {
        if (instrumentId == null) {
            return Optional.empty();
        }
        CachedEntry entry = cache.get(instrumentId);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry)) {
            cache.remove(instrumentId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.snapshot);
    }

    private boolean isExpired(CachedEntry entry) {
        Duration ttl = properties.getTtl();
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        Instant expiresAt = entry.cachedAt.plus(ttl);
        return Instant.now().isAfter(expiresAt);
    }

    private record CachedEntry(MarkPriceSnapshot snapshot, Instant cachedAt) {
    }
}
