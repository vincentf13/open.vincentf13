package open.vincentf13.exchange.risk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RiskPreCheckProperties.class)
public class RiskMarginConfiguration {
}
