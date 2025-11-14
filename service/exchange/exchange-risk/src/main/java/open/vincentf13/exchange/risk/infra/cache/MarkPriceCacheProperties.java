package open.vincentf13.exchange.risk.infra.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "exchange.risk.mark-price-cache")
public class MarkPriceCacheProperties {

    /**
     * TTL for cached mark price snapshots. Defaults to 5 seconds which is aligned with mark price refresh cadence.
     */
    private Duration ttl = Duration.ofSeconds(5);
}
