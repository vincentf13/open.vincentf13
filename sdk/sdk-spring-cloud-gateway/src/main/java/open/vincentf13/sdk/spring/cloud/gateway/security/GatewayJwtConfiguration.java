package open.vincentf13.sdk.spring.cloud.gateway.security;

import open.vincentf13.sdk.auth.jwt.session.JwtSessionService;
import open.vincentf13.sdk.auth.jwt.config.JwtProperties;
import open.vincentf13.sdk.auth.jwt.OpenJwtService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayJwtProperties.class)
@ConditionalOnProperty(prefix = "open.vincentf13.security.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(OpenJwtService.class)
public class GatewayJwtConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtGatewayFilter jwtGatewayFilter(OpenJwtService openJwtService,
                                             JwtProperties jwtProperties,
                                             ObjectProvider<JwtSessionService> sessionServiceProvider,
                                             GatewayJwtProperties properties) {
        return new JwtGatewayFilter(openJwtService, jwtProperties, sessionServiceProvider, properties);
    }
}
