package open.vincentf13.exchange.risk.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "exchange.risk.precheck")
public class RiskPreCheckProperties {

    /**
     * Additional buffer added to maintenance margin rate when simulating post-order margin ratio.
     */
    private BigDecimal maintenanceBuffer = new BigDecimal("0.001");

    /**
     * Asset code used when freezing margin (e.g. USDT).
     */
    private String marginAsset = "USDT";
}
