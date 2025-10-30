package open.vincentf13.common.sdk.spring.cloud.gateway.security;

import open.vincentf13.common.infra.jwt.session.JwtSessionService;
import open.vincentf13.common.infra.jwt.token.JwtProperties;
import open.vincentf13.common.infra.jwt.token.OpenJwt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayJwtProperties.class)
@ConditionalOnProperty(prefix = "open.vincentf13.security.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GatewayJwtConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtGatewayFilter jwtGatewayFilter(OpenJwt openJwt,
                                             JwtProperties jwtProperties,
                                             ObjectProvider<JwtSessionService> sessionServiceProvider,
                                             GatewayJwtProperties properties) {
        return new JwtGatewayFilter(openJwt, jwtProperties, sessionServiceProvider, properties);
    }
}
