package open.vincentf13.sdk.spring.security.config;

import open.vincentf13.sdk.auth.apikey.filter.ApiKeyAuthenticationFilter;
import open.vincentf13.sdk.auth.jwt.config.JwtConfigurer;
import open.vincentf13.sdk.auth.jwt.filter.JwtFilter;
import open.vincentf13.sdk.spring.security.auth.AnnotationBasedAuthorizationManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

public class OpenSecurityConfigurer extends AbstractHttpConfigurer<OpenSecurityConfigurer, HttpSecurity> {

    private final ObjectProvider<ApiKeyAuthenticationFilter> apiKeyFilterProvider;
    private final AnnotationBasedAuthorizationManager authorizationManager;
    private final JwtConfigurer jwtConfigurer;

    public OpenSecurityConfigurer(ObjectProvider<ApiKeyAuthenticationFilter> apiKeyFilterProvider,
                                  AnnotationBasedAuthorizationManager authorizationManager,
                                  JwtConfigurer jwtConfigurer) {
        this.apiKeyFilterProvider = apiKeyFilterProvider;
        this.authorizationManager = authorizationManager;
        this.jwtConfigurer = jwtConfigurer;
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.apply(jwtConfigurer).and()
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().access(authorizationManager));

        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter = apiKeyFilterProvider.getIfAvailable();
        if (apiKeyAuthenticationFilter != null) {
            http.addFilterBefore(apiKeyAuthenticationFilter, JwtFilter.class);
        }
    }
}
