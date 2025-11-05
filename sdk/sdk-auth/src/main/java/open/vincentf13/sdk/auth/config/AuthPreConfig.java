package open.vincentf13.sdk.auth.config;

import open.vincentf13.sdk.auth.auth.AnnotationBasedAuthorizationManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

public class AuthPreConfig extends AbstractHttpConfigurer<AuthPreConfig, HttpSecurity> {

    private final ObjectProvider<ApiKeyPreConfig> apiKeySecurityConfigurerProvider;
    private final AnnotationBasedAuthorizationManager authorizationManager;
    private final JwtPreConfig jwtPreConfig;

    public AuthPreConfig(ObjectProvider<ApiKeyPreConfig> apiKeySecurityConfigurerProvider,
                         AnnotationBasedAuthorizationManager authorizationManager,
                         JwtPreConfig jwtPreConfig) {
        this.apiKeySecurityConfigurerProvider = apiKeySecurityConfigurerProvider;
        this.authorizationManager = authorizationManager;
        this.jwtPreConfig = jwtPreConfig;
    }

    @Override
    public void init(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().access(authorizationManager));
        http.formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        http.apply(jwtPreConfig);

        ApiKeyPreConfig apiKeyConfigurer = apiKeySecurityConfigurerProvider.getIfAvailable();
        if (apiKeyConfigurer != null) {
            http.apply(apiKeyConfigurer);
        }
    }
}
