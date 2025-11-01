package open.vincentf13.sdk.auth.server.config;

import open.vincentf13.sdk.auth.jwt.config.JwtConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AuthServerSecurityConfiguration {

    /**
     * The order is set to a value lower than the default (which is provided by sdk-spring-security)
     * to ensure this configuration takes precedence for services that include the sdk-auth-server module.
     */
    @Bean
    @Order(99)
    public SecurityFilterChain authServerSecurityFilterChain(HttpSecurity http, JwtConfigurer jwtConfigurer) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/auth/login", "/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .apply(jwtConfigurer);

        return http.build();
    }
}
