package open.vincentf13.common.infra.jwt.config;

import open.vincentf13.common.infra.jwt.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class JwtSecurityConfigurer extends AbstractHttpConfigurer<JwtSecurityConfigurer, HttpSecurity> {

    private final ObjectProvider<JwtAuthenticationFilter> filterProvider;

    public JwtSecurityConfigurer(ObjectProvider<JwtAuthenticationFilter> filterProvider) {
        this.filterProvider = filterProvider;
    }

    @Override
    public void init(HttpSecurity http) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login", "/public/**").permitAll()
                .anyRequest().authenticated())
            .csrf(AbstractHttpConfigurer::disable);
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter filter = filterProvider.getIfAvailable();
        if (filter == null) {
            throw new IllegalStateException("JwtAuthenticationFilter bean not available");
        }
        http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
    }
}
