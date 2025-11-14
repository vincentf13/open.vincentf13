package open.vincentf13.exchange.risk.config;

import open.vincentf13.exchange.risk.infra.cache.MarkPriceCacheProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MarkPriceCacheProperties.class, RiskPreCheckProperties.class})
public class RiskMarginConfiguration {
}
