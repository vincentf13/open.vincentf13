package open.vincentf13.sdk.auth.jwt.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class JwtSecurityAutoConfiguration {

    @Bean
    @Order
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http,
                                                      JwtConfigurer configurer) throws Exception {
        http.apply(configurer);
        return http.build();
    }
}
