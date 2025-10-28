package open.vincentf13.common.sdk.spring.security.config;

import open.vincentf13.common.sdk.spring.security.handler.LoginFailureHandler;
import open.vincentf13.common.sdk.spring.security.handler.LoginSuccessHandler;
import open.vincentf13.common.sdk.spring.security.filter.JwtAuthenticationFilter;
import open.vincentf13.common.sdk.spring.security.token.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class})
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   LoginSuccessHandler successHandler,
                                                   LoginFailureHandler failureHandler,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login", "/public/**").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login")
                                   .loginProcessingUrl("/api/login")
                                   .successHandler(successHandler)
                                   .failureHandler(failureHandler)
                                   .permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
