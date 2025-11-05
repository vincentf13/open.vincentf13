package open.vincentf13.sdk.auth.config;

import open.vincentf13.sdk.auth.jwt.JwtFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class JwtPreConfig extends AbstractHttpConfigurer<JwtPreConfig, HttpSecurity> {

    private final ObjectProvider<JwtFilter> filterProvider;

    public JwtPreConfig(ObjectProvider<JwtFilter> filterProvider) {
        this.filterProvider = filterProvider;
    }

    @Override
    public void init(HttpSecurity http) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        JwtFilter filter = filterProvider.getIfAvailable();
        if (filter == null) {
            throw new IllegalStateException("JwtAuthenticationFilter bean not available");
        }
        http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
    }
}
