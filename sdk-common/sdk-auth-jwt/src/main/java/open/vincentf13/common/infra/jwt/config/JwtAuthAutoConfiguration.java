package open.vincentf13.common.infra.jwt.config;

import open.vincentf13.common.infra.jwt.filter.JwtAuthenticationFilter;
import open.vincentf13.common.infra.jwt.session.InMemoryJwtSessionStore;
import open.vincentf13.common.infra.jwt.session.JwtSessionService;
import open.vincentf13.common.infra.jwt.session.JwtSessionStore;
import open.vincentf13.common.infra.jwt.session.RedisJwtSessionStore;
import open.vincentf13.common.infra.jwt.token.JwtProperties;
import open.vincentf13.common.infra.jwt.token.OpenJwtToken;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http,
                                                      JwtSecurityConfigurer configurer) throws Exception {
        http.apply(configurer);
        return http.build();
    }


    @Bean
    @ConditionalOnMissingBean
    public OpenJwtToken openJwtToken(JwtProperties properties) {
        return new OpenJwtToken(properties);
    }

    @Bean
    @ConditionalOnClass(RedisTemplate.class)
    @ConditionalOnBean(RedisTemplate.class)
    @ConditionalOnMissingBean
    public JwtSessionStore redisJwtSessionStore(RedisTemplate<String, Object> redisTemplate,
                                                JwtProperties properties) {
        return new RedisJwtSessionStore(redisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(JwtSessionStore.class)
    public JwtSessionStore inMemoryJwtSessionStore() {
        return new InMemoryJwtSessionStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtSessionService jwtSessionService(OpenJwtToken jwtToken,
                                               JwtSessionStore sessionStore) {
        return new JwtSessionService(jwtToken, sessionStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(OpenJwtToken openJwtToken,
                                                           ObjectProvider<JwtSessionService> sessionServiceProvider,
                                                           JwtProperties properties) {
        return new JwtAuthenticationFilter(openJwtToken, sessionServiceProvider, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtSecurityConfigurer jwtSecurityConfigurer(ObjectProvider<JwtAuthenticationFilter> provider) {
        return new JwtSecurityConfigurer(provider);
    }

}
