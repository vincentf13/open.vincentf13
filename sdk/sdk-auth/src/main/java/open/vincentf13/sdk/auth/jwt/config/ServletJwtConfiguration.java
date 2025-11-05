package open.vincentf13.sdk.auth.jwt.config;

import open.vincentf13.sdk.auth.jwt.filter.JwtFilter;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionService;
import open.vincentf13.sdk.auth.jwt.token.JwtProperties;
import open.vincentf13.sdk.auth.jwt.token.OpenJwt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServletJwtConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtFilter jwtAuthenticationFilter(OpenJwt openJwt,
                                             ObjectProvider<JwtSessionService> sessionServiceProvider,
                                             JwtProperties properties) {
        return new JwtFilter(openJwt, sessionServiceProvider, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtPreConfig jwtSecurityConfigurer(ObjectProvider<JwtFilter> provider) {
        return new JwtPreConfig(provider);
    }
}
