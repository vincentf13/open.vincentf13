package open.vincentf13.sdk.spring.security.config;

import open.vincentf13.sdk.auth.apikey.config.ApiKeyAutoConfiguration;
import open.vincentf13.sdk.auth.apikey.config.ApiKeySecurityConfigurer;
import open.vincentf13.sdk.auth.jwt.config.JwtConfigurer;
import open.vincentf13.sdk.auth.jwt.config.JwtSecurityAutoConfiguration;
import open.vincentf13.sdk.spring.security.auth.AnnotationBasedAuthorizationManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
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
@AutoConfigureBefore(JwtSecurityAutoConfiguration.class)
@AutoConfigureAfter(ApiKeyAutoConfiguration.class)
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AnnotationBasedAuthorizationManager annotationBasedAuthorizationManager(RequestMappingHandlerMapping handlerMapping) {
        return new AnnotationBasedAuthorizationManager(handlerMapping);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenSecurityConfigurer openSecurityConfigurer(ObjectProvider<ApiKeySecurityConfigurer> apiKeySecurityConfigurerProvider,
                                                         AnnotationBasedAuthorizationManager authorizationManager,
                                                         JwtConfigurer jwtConfigurer) {
        return new OpenSecurityConfigurer(apiKeySecurityConfigurerProvider, authorizationManager, jwtConfigurer);
    }

    @Bean
    @Order(100)
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          OpenSecurityConfigurer openSecurityConfigurer
                                                         ) throws Exception {

        http.apply(openSecurityConfigurer);
        return http.build();
    }
}

