package open.vincentf13.sdk.auth.jwt;

import open.vincentf13.sdk.auth.jwt.session.JwtSessionService;
import open.vincentf13.sdk.auth.jwt.token.OpenJwtService;
import open.vincentf13.sdk.auth.jwt.token.config.JwtProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JwtConfig {

  @Bean
  @ConditionalOnMissingBean
  public JwtFilter jwtAuthenticationFilter(
      OpenJwtService openJwtService,
      ObjectProvider<JwtSessionService> sessionServiceProvider,
      JwtProperties properties) {
    return new JwtFilter(openJwtService, sessionServiceProvider, properties);
  }
}
