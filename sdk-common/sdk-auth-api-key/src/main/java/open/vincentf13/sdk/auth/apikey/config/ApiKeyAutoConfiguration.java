package open.vincentf13.sdk.auth.apikey.config;

import open.vincentf13.sdk.auth.apikey.key.ApiKeyProperties;
import open.vincentf13.sdk.auth.apikey.key.ApiKeyValidator;
import open.vincentf13.sdk.auth.apikey.key.PropertiesApiKeyValidator;
import open.vincentf13.sdk.auth.apikey.filter.ApiKeyAuthenticationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(ApiKeyValidator apiKeyValidator, RequestMappingHandlerMapping handlerMapping) {
        return new ApiKeyAuthenticationFilter(apiKeyValidator, handlerMapping);
    }

}
