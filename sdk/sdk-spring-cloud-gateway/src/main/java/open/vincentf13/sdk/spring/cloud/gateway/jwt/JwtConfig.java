package open.vincentf13.sdk.spring.cloud.gateway.jwt;

import open.vincentf13.sdk.auth.jwt.OpenJwtService;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@ConditionalOnProperty(prefix = "open.vincentf13.security.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(OpenJwtService.class)
public class JwtConfig {

    @Bean
    @ConditionalOnMissingBean
    public JwtFilter jwtGatewayFilter(OpenJwtService openJwtService,
                                      ObjectProvider<JwtSessionService> sessionServiceProvider,
                                      JwtProperties properties) {
        return new JwtFilter(openJwtService, sessionServiceProvider, properties);
    }
}
