package open.vincentf13.sdk.spring.cloud.openfeign.retry.config;

import feign.Retryer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class FeignRetryAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(Retryer.class)
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
