package open.vincentf13.sdk.auth.apikey.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@AutoConfigureAfter(ApiKeyAutoConfiguration.class)
public class ApiKeySecurityAutoConfiguration {

    @Bean
    @Order
    @ConditionalOnBean(ApiKeySecurityConfigurer.class)
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain apiKeySecurityFilterChain(HttpSecurity http,
                                                         ApiKeySecurityConfigurer configurer) throws Exception {
        http.apply(configurer);
        return http.build();
    }
}
