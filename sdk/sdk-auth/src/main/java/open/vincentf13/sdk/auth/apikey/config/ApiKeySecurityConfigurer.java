package open.vincentf13.sdk.auth.apikey.config;

import open.vincentf13.sdk.auth.apikey.filter.ApiKeyAuthenticationFilter;
import open.vincentf13.sdk.auth.jwt.filter.JwtFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Applies the ApiKeyAuthenticationFilter to the security chain. When a JwtFilter is present
 * the API key filter is positioned just ahead of it, otherwise it falls back to the standard
 * UsernamePasswordAuthenticationFilter slot.
 */
public class ApiKeySecurityConfigurer extends AbstractHttpConfigurer<ApiKeySecurityConfigurer, HttpSecurity> {

    private final ObjectProvider<ApiKeyAuthenticationFilter> apiKeyFilterProvider;
    private final ObjectProvider<JwtFilter> jwtFilterProvider;

    public ApiKeySecurityConfigurer(ObjectProvider<ApiKeyAuthenticationFilter> apiKeyFilterProvider,
                                    ObjectProvider<JwtFilter> jwtFilterProvider) {
        this.apiKeyFilterProvider = apiKeyFilterProvider;
        this.jwtFilterProvider = jwtFilterProvider;
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter = apiKeyFilterProvider.getIfAvailable();
        if (apiKeyAuthenticationFilter == null) {
            return;
        }

        JwtFilter jwtFilter = jwtFilterProvider.getIfAvailable();
        if (jwtFilter != null) {
            http.addFilterBefore(apiKeyAuthenticationFilter, jwtFilter.getClass());
            return;
        }

        http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
