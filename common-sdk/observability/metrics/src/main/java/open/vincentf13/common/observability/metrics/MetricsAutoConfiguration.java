package open.vincentf13.common.observability.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Shared metrics setup (logging warm-ups etc.).
 */
@AutoConfiguration
public class MetricsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityAutoConfiguration.class);

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public Runnable meterRegistryWarmup(MeterRegistry meterRegistry) {
        log.info("Metrics auto-configuration active; registry type={}", meterRegistry.getClass().getSimpleName());
        return () -> { };
    }
}
