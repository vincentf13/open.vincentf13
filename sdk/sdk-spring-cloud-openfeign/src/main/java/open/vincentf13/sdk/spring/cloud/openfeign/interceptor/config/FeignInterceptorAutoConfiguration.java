package open.vincentf13.sdk.spring.cloud.openfeign.interceptor.config;

import feign.RequestInterceptor;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.DefaultFeignRequestInterceptor;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.apikey.FeignApiKeyProperties;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.apikey.FeignApiKeyProvider;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.apikey.PropertiesFeignApiKeyProvider;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.jwt.FeignAuthorizationProvider;
import open.vincentf13.sdk.spring.cloud.openfeign.interceptor.jwt.RequestHeaderFeignAuthorizationProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(FeignApiKeyProperties.class)
public class FeignInterceptorAutoConfiguration {
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
  public RequestInterceptor defaultFeignRequestInterceptor(
      FeignAuthorizationProvider authorizationProvider, FeignApiKeyProvider apiKeyProvider) {
    return new DefaultFeignRequestInterceptor(authorizationProvider, apiKeyProvider);
  }
}
