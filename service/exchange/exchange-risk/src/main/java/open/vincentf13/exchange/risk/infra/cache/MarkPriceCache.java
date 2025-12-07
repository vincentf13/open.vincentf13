package open.vincentf13.exchange.risk.infra.cache;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MarkPriceCache {

    private final ConcurrentMap<Long, BigDecimal> cache = new ConcurrentHashMap<>();

    public Optional<BigDecimal> get(Long instrumentId) {
        return Optional.ofNullable(cache.get(instrumentId));
    }

    public void put(Long instrumentId, BigDecimal markPrice) {
        if (markPrice != null) {
            cache.put(instrumentId, markPrice);
        }
    }
}
