 package open.vincentf13.sdk.core.metrics.config;

 import io.micrometer.core.instrument.MeterRegistry;
 import open.vincentf13.sdk.core.metrics.Metrics;
 import org.springframework.boot.autoconfigure.AutoConfiguration;
 import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
 import org.springframework.context.annotation.Bean;
 import org.springframework.beans.factory.annotation.Value;

 @AutoConfiguration
 @ConditionalOnBean(MeterRegistry.class)
 public class MetricsAutoConfig {
    @Bean
    public Object initMetrics(MeterRegistry registry,
                              @Value("${spring.application.name:app}") String app,
                              @Value("${APP_ENV:dev}") String env) {
        Metrics.init(registry, app, env);
        return new Object();
    }
 }
