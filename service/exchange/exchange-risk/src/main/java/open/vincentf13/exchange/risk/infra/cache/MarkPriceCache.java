package open.vincentf13.exchange.risk.infra.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import open.vincentf13.exchange.risk.domain.model.MarkPriceSnapshot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class MarkPriceCache {

    private final Cache<Long, MarkPriceSnapshot> cache;

    @Getter
    private final MarkPriceCacheProperties properties;

    public MarkPriceCache(MarkPriceCacheProperties properties) {
        this.properties = properties;
        this.cache = buildCache(properties);
    }

    public void put(MarkPriceSnapshot snapshot) {
        if (snapshot == null || snapshot.getInstrumentId() == null) {
            return;
        }
        cache.put(snapshot.getInstrumentId(), snapshot);
    }

    public Optional<MarkPriceSnapshot> get(Long instrumentId) {
        if (instrumentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.getIfPresent(instrumentId));
    }

    private Cache<Long, MarkPriceSnapshot> buildCache(MarkPriceCacheProperties properties) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        Duration ttl = properties.getTtl();
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            builder.expireAfterWrite(ttl);
        }
        long maximumSize = Math.max(1L, properties.getMaximumSize());
        builder.maximumSize(maximumSize);
        return builder.build();
    }
}
