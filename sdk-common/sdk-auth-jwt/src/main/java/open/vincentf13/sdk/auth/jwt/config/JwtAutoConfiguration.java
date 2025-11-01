package open.vincentf13.sdk.auth.jwt.config;

import open.vincentf13.sdk.auth.jwt.filter.JwtFilter;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionService;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionStore;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionStoreInMemory;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionStoreRedis;
import open.vincentf13.sdk.auth.jwt.token.JwtProperties;
import open.vincentf13.sdk.auth.jwt.token.OpenJwt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {



    @Bean
    @ConditionalOnMissingBean
    public OpenJwt openJwtToken(JwtProperties properties) {
        return new OpenJwt(properties);
    }

    @Bean
    @ConditionalOnClass(RedisTemplate.class)
    @ConditionalOnBean(RedisTemplate.class)
    @ConditionalOnMissingBean
    public JwtSessionStore redisJwtSessionStore(RedisTemplate<String, Object> redisTemplate,
                                                JwtProperties properties) {
        return new JwtSessionStoreRedis(redisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(JwtSessionStore.class)
    public JwtSessionStore inMemoryJwtSessionStore() {
        return new JwtSessionStoreInMemory();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtSessionService jwtSessionService(JwtSessionStore sessionStore) {
        return new JwtSessionService(sessionStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtFilter jwtAuthenticationFilter(OpenJwt openJwt,
                                             ObjectProvider<JwtSessionService> sessionServiceProvider,
                                             JwtProperties properties) {
        return new JwtFilter(openJwt, sessionServiceProvider, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtConfigurer jwtSecurityConfigurer(ObjectProvider<JwtFilter> provider) {
        return new JwtConfigurer(provider);
    }
}
