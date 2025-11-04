package open.vincentf13.sdk.spring.security.config;

import open.vincentf13.sdk.auth.apikey.config.ApiKeySecurityConfigurer;
import open.vincentf13.sdk.auth.jwt.config.JwtConfigurer;
import open.vincentf13.sdk.spring.security.auth.AnnotationBasedAuthorizationManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

public class OpenSecurityConfigurer extends AbstractHttpConfigurer<OpenSecurityConfigurer, HttpSecurity> {

    private final ObjectProvider<ApiKeySecurityConfigurer> apiKeySecurityConfigurerProvider;
    private final AnnotationBasedAuthorizationManager authorizationManager;
    private final JwtConfigurer jwtConfigurer;

    public OpenSecurityConfigurer(ObjectProvider<ApiKeySecurityConfigurer> apiKeySecurityConfigurerProvider,
                                  AnnotationBasedAuthorizationManager authorizationManager,
                                  JwtConfigurer jwtConfigurer) {
        this.apiKeySecurityConfigurerProvider = apiKeySecurityConfigurerProvider;
        this.authorizationManager = authorizationManager;
        this.jwtConfigurer = jwtConfigurer;
    }

    @Override
    public void init(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().access(authorizationManager));
        http.formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        http.apply(jwtConfigurer);

        ApiKeySecurityConfigurer apiKeyConfigurer = apiKeySecurityConfigurerProvider.getIfAvailable();
        if (apiKeyConfigurer != null) {
            http.apply(apiKeyConfigurer);
        }
    }
}
