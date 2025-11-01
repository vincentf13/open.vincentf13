package open.vincentf13.sdk.spring.security.config;

import open.vincentf13.sdk.auth.apikey.config.ApiKeyAutoConfiguration;
import open.vincentf13.sdk.auth.apikey.filter.ApiKeyAuthenticationFilter;
import open.vincentf13.sdk.auth.jwt.config.JwtAutoConfiguration;
import open.vincentf13.sdk.auth.jwt.config.JwtConfigurer;
import open.vincentf13.sdk.auth.jwt.filter.JwtFilter;
import open.vincentf13.sdk.spring.security.auth.AnnotationBasedAuthorizationManager;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
@ConditionalOnWebApplication
@Import({JwtAutoConfiguration.class, ApiKeyAutoConfiguration.class})
@AutoConfigureAfter({JwtAutoConfiguration.class, ApiKeyAutoConfiguration.class})
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AnnotationBasedAuthorizationManager annotationBasedAuthorizationManager(RequestMappingHandlerMapping handlerMapping) {
        return new AnnotationBasedAuthorizationManager(handlerMapping);
    }


    @Bean
    @Order(100)
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain defaultSecurityFilterChain(
            HttpSecurity http,
            JwtConfigurer jwtConfigurer,
            AnnotationBasedAuthorizationManager authorizationManager,
            org.springframework.beans.factory.ObjectProvider<ApiKeyAuthenticationFilter> apiKeyFilterProvider) throws Exception {

        http.apply(jwtConfigurer)
                .and()
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().access(authorizationManager));

        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter = apiKeyFilterProvider.getIfAvailable();
        if (apiKeyAuthenticationFilter != null) {
            http.addFilterBefore(apiKeyAuthenticationFilter, JwtFilter.class);
        }

        return http.build();
    }
}
