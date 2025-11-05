package open.vincentf13.sdk.auth.config;

import open.vincentf13.sdk.auth.jwt.config.JwtPreConfig;
import open.vincentf13.sdk.auth.auth.AnnotationBasedAuthorizationManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
@ConditionalOnWebApplication
@AutoConfigureAfter(ApiKeyAutoConfig.class)
public class AuthConfig {

    @Bean
    @ConditionalOnMissingBean
    public AnnotationBasedAuthorizationManager annotationBasedAuthorizationManager(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping requestMappingHandlerMapping) {
        return new AnnotationBasedAuthorizationManager(requestMappingHandlerMapping);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthPreConfig openSecurityConfigurer(ObjectProvider<ApiKeyPreConfig> apiKeySecurityConfigurerProvider,
                                                AnnotationBasedAuthorizationManager authorizationManager,
                                                JwtPreConfig jwtPreConfig) {
        return new AuthPreConfig(apiKeySecurityConfigurerProvider, authorizationManager, jwtPreConfig);
    }

    @Bean
    @Order(100)
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          AuthPreConfig authPreConfig
                                                         ) throws Exception {

        http.apply(authPreConfig);
        return http.build();
    }
}
