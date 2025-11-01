package open.vincentf13.sdk.spring.cloud.openfeign;

import feign.RequestInterceptor;
import feign.Retryer;
import open.vincentf13.sdk.core.OpenConstant;
import open.vincentf13.sdk.spring.cloud.openfeign.auth.FeignAuthorizationProvider;
import open.vincentf13.sdk.spring.cloud.openfeign.auth.NoOpFeignAuthorizationProvider;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.DefaultFeignRequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(EnableFeignClients.class)
@ConditionalOnProperty(prefix = "spring.cloud.openfeign", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableFeignClients(basePackages = OpenConstant.BASE_PACKAGE)
public class OpenFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Retryer.class)
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    @ConditionalOnMissingBean(FeignAuthorizationProvider.class)
    public FeignAuthorizationProvider feignAuthorizationProvider() {
        return new NoOpFeignAuthorizationProvider();
    }

    @Bean
    @ConditionalOnMissingBean(name = "defaultFeignRequestInterceptor")
    public RequestInterceptor defaultFeignRequestInterceptor(FeignAuthorizationProvider authorizationProvider) {
        return new DefaultFeignRequestInterceptor(authorizationProvider);
    }
}
