package open.vincentf13.sdk.spring.cloud.openfeign.config;

import feign.FeignException;
import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.slf4j.Slf4jLogger;
import open.vincentf13.sdk.core.OpenConstant;
import open.vincentf13.sdk.spring.cloud.openfeign.FeignExceptionHandler;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.apikey.FeignApiKeyProperties;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.apikey.FeignApiKeyProvider;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.apikey.PropertiesFeignApiKeyProvider;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.jwt.FeignAuthorizationProvider;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.jwt.RequestHeaderFeignAuthorizationProvider;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.DefaultFeignRequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.web.client.RestTemplate;

@AutoConfiguration
@ConditionalOnClass({FeignException.class, EnableFeignClients.class})
@ConditionalOnProperty(prefix = "spring.cloud.openfeign", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FeignApiKeyProperties.class)
@EnableFeignClients(basePackages = OpenConstant.BASE_PACKAGE)
@Import(FeignExceptionHandler.class)
public class OpenFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnMissingBean(Retryer.class)
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    @ConditionalOnMissingBean(FeignAuthorizationProvider.class)
    public FeignAuthorizationProvider feignAuthorizationProvider() {
        return new RequestHeaderFeignAuthorizationProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public FeignApiKeyProvider feignApiKeyProvider(FeignApiKeyProperties properties) {
        return new PropertiesFeignApiKeyProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "defaultFeignRequestInterceptor")
    public RequestInterceptor defaultFeignRequestInterceptor(FeignAuthorizationProvider authorizationProvider,
                                                             FeignApiKeyProvider apiKeyProvider) {
        return new DefaultFeignRequestInterceptor(authorizationProvider, apiKeyProvider);
    }

    @Bean
    @ConditionalOnMissingBean(Logger.class)
    public Logger feignLogger() {
        return new Slf4jLogger("feign.http");
    }

    @Bean
    @ConditionalOnMissingBean(Logger.Level.class)
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}
