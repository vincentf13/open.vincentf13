package open.vincentf13.sdk.spring.cloud.gateway.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@ConditionalOnClass({ServerHttpSecurity.class, SecurityWebFilterChain.class})
@ConditionalOnProperty(
    prefix = "open.vincentf13.security.gateway",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SecurityConfig {

  @Bean
  @ConditionalOnMissingBean(SecurityWebFilterChain.class)
  public SecurityWebFilterChain gatewaySecurityWebFilterChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
        .build();
  }
}
