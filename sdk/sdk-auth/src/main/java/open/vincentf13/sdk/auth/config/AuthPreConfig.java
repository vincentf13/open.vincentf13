package open.vincentf13.sdk.auth.config;

import open.vincentf13.sdk.auth.apikey.ApiKeyFilter;
import open.vincentf13.sdk.auth.auth.AnnotationBasedAuthorizationManager;
import open.vincentf13.sdk.auth.jwt.JwtFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class AuthPreConfig extends AbstractHttpConfigurer<AuthPreConfig, HttpSecurity> {

    private final ObjectProvider<ApiKeyFilter> apiKeyFilterProvider;
    private final ObjectProvider<JwtFilter> jwtFilterProvider;
    private final AnnotationBasedAuthorizationManager authorizationManager;

    public AuthPreConfig(ObjectProvider<ApiKeyFilter> apiKeyFilterProvider,
                         ObjectProvider<JwtFilter> jwtFilterProvider,
                         AnnotationBasedAuthorizationManager authorizationManager) {
        this.apiKeyFilterProvider = apiKeyFilterProvider;
        this.jwtFilterProvider = jwtFilterProvider;
        this.authorizationManager = authorizationManager;
    }

    @Override
    public void init(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().access(authorizationManager));
        http.formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        JwtFilter jwtFilter = jwtFilterProvider.getIfAvailable();
        if (jwtFilter == null) {
            throw new IllegalStateException("JwtAuthenticationFilter bean not available");
        }
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        ApiKeyFilter apiKeyFilter = apiKeyFilterProvider.getIfAvailable();
        if (apiKeyFilter == null) {
            return;
        }

        http.addFilterBefore(apiKeyFilter, jwtFilter.getClass());
    }
}
