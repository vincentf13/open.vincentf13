package open.vincentf13.sdk.auth.config;

import open.vincentf13.sdk.auth.apikey.ApiKeyFilter;
import open.vincentf13.sdk.auth.auth.AnnotationBasedAuthorizationManager;
import open.vincentf13.sdk.auth.jwt.JwtFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
    @Order(100)
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          ObjectProvider<ApiKeyFilter> apiKeyFilterProvider,
                                                          ObjectProvider<JwtFilter> jwtFilterProvider,
                                                          AnnotationBasedAuthorizationManager authorizationManager
                                                         ) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().access(authorizationManager));

        http.formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);

        JwtFilter jwtFilter = jwtFilterProvider.getIfAvailable();
        if (jwtFilter == null) {
            throw new IllegalStateException("JwtAuthenticationFilter bean not available");
        }
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        ApiKeyFilter apiKeyFilter = apiKeyFilterProvider.getIfAvailable();
        if (apiKeyFilter != null) {
            http.addFilterBefore(apiKeyFilter, jwtFilter.getClass());
        }

        return http.build();
    }
}
