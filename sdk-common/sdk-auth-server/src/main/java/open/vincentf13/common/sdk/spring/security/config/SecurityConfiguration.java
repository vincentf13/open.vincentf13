package open.vincentf13.common.sdk.spring.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.common.infra.jwt.filter.JwtAuthenticationFilter;
import open.vincentf13.common.infra.jwt.session.JwtSessionService;
import open.vincentf13.common.infra.jwt.token.JwtProperties;
import open.vincentf13.common.infra.jwt.token.OpenJwtToken;
import open.vincentf13.common.sdk.spring.security.handler.LoginFailureHandler;
import open.vincentf13.common.sdk.spring.security.handler.LoginSuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
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
    @ConditionalOnMissingBean
    public LoginSuccessHandler loginSuccessHandler(ObjectProvider<ObjectMapper> objectMapperProvider,
                                                   MessageSource messageSource,
                                                   JwtSessionService sessionService) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new LoginSuccessHandler(mapper, messageSource, sessionService);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginFailureHandler loginFailureHandler(ObjectProvider<ObjectMapper> objectMapperProvider,
                                                   MessageSource messageSource) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new LoginFailureHandler(mapper, messageSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(OpenJwtToken openJwtToken,
                                                           ObjectProvider<JwtSessionService> sessionServiceProvider,
                                                           JwtProperties properties) {
        return new JwtAuthenticationFilter(openJwtToken, sessionServiceProvider, properties);
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
