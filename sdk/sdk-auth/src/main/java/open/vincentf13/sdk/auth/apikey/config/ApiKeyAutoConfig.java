package open.vincentf13.sdk.auth.apikey.config;

import open.vincentf13.sdk.auth.apikey.ApiKeyFilter;
import open.vincentf13.sdk.auth.apikey.ApiKeyValidator;
import open.vincentf13.sdk.auth.apikey.ApiKeyValidatorImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
@EnableConfigurationProperties(ApiKeyProperties.class)
@ConditionalOnProperty(name = "security.api-key.enabled", havingValue = "true")
public class ApiKeyAutoConfig {

  @Bean
  @ConditionalOnMissingBean
  public ApiKeyValidator apiKeyValidator(ApiKeyProperties properties) {
    return new ApiKeyValidatorImpl(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(ApiKeyValidator.class)
  public ApiKeyFilter apiKeyAuthenticationFilter(
      ApiKeyValidator apiKeyValidator,
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
    return new ApiKeyFilter(apiKeyValidator, handlerMapping);
  }
}
