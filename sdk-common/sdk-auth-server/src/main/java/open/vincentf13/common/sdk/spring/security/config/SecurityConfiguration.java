package open.vincentf13.common.sdk.spring.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.common.infra.jwt.config.JwtAutoConfiguration;
import open.vincentf13.common.infra.jwt.config.JwtConfigurer;
import open.vincentf13.common.infra.jwt.token.JwtProperties;
import open.vincentf13.common.sdk.spring.security.handler.LoginFailureHandler;
import open.vincentf13.common.sdk.spring.security.handler.LoginSuccessHandler;
import open.vincentf13.common.sdk.spring.security.service.AuthJwtSessionService;
import open.vincentf13.common.sdk.spring.security.store.AuthJwtSessionStoreConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class})
@Import({AuthJwtSessionStoreConfiguration.class, JwtAutoConfiguration.class})
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginSuccessHandler loginSuccessHandler(ObjectProvider<ObjectMapper> objectMapperProvider,
                                                   MessageSource messageSource,
                                                   AuthJwtSessionService sessionService) {
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   LoginSuccessHandler successHandler,
                                                   LoginFailureHandler failureHandler,
                                                   JwtConfigurer jwtConfigurer) throws Exception {
        http.apply(jwtConfigurer)
                .and()
                .formLogin(form -> form.loginPage("/login")
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout.disable());
        return http.build();
    }
}
