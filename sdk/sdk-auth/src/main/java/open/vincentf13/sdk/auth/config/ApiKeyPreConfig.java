package open.vincentf13.sdk.auth.config;

import open.vincentf13.sdk.auth.apikey.ApiKeyFilter;
import open.vincentf13.sdk.auth.jwt.JwtFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Applies the ApiKeyAuthenticationFilter to the security chain. When a JwtFilter is present
 * the API key filter is positioned just ahead of it, otherwise it falls back to the standard
 * UsernamePasswordAuthenticationFilter slot.
 */
public class ApiKeyPreConfig extends AbstractHttpConfigurer<ApiKeyPreConfig, HttpSecurity> {

    private final ObjectProvider<ApiKeyFilter> apiKeyFilterProvider;
    private final ObjectProvider<JwtFilter> jwtFilterProvider;

    public ApiKeyPreConfig(ObjectProvider<ApiKeyFilter> apiKeyFilterProvider,
                           ObjectProvider<JwtFilter> jwtFilterProvider) {
        this.apiKeyFilterProvider = apiKeyFilterProvider;
        this.jwtFilterProvider = jwtFilterProvider;
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        ApiKeyFilter apiKeyFilter = apiKeyFilterProvider.getIfAvailable();
        if (apiKeyFilter == null) {
            return;
        }

        JwtFilter jwtFilter = jwtFilterProvider.getIfAvailable();
        if (jwtFilter != null) {
            http.addFilterBefore(apiKeyFilter, jwtFilter.getClass());
            return;
        }

        http.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
