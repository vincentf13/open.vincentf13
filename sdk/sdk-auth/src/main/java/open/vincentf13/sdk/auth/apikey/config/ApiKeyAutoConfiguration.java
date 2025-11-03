package open.vincentf13.sdk.auth.apikey.config;

import open.vincentf13.sdk.auth.apikey.filter.ApiKeyAuthenticationFilter;
import open.vincentf13.sdk.auth.apikey.key.ApiKeyProperties;
import open.vincentf13.sdk.auth.apikey.key.ApiKeyValidator;
import open.vincentf13.sdk.auth.apikey.key.PropertiesApiKeyValidator;
import open.vincentf13.sdk.auth.jwt.filter.JwtFilter;
import org.springframework.beans.factory.ObjectProvider;
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
public class ApiKeyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyValidator apiKeyValidator(ApiKeyProperties properties) {
        return new PropertiesApiKeyValidator(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ApiKeyValidator.class)
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(ApiKeyValidator apiKeyValidator,
                                                                 @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        return new ApiKeyAuthenticationFilter(apiKeyValidator, handlerMapping);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ApiKeyAuthenticationFilter.class)
    public ApiKeySecurityConfigurer apiKeySecurityConfigurer(ObjectProvider<ApiKeyAuthenticationFilter> apiKeyFilterProvider,
                                                             ObjectProvider<JwtFilter> jwtFilterProvider) {
        return new ApiKeySecurityConfigurer(apiKeyFilterProvider, jwtFilterProvider);
    }
}
