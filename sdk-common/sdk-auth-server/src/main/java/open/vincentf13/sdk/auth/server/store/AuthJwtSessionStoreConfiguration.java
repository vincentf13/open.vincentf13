package open.vincentf13.sdk.auth.server.store;

import open.vincentf13.sdk.auth.jwt.token.JwtProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;

@Configuration
public class AuthJwtSessionStoreConfiguration {

    @Bean
    @Primary
    public AuthJwtSessionStore authJwtSessionStore(Optional<RedisTemplate<String, Object>> redisTemplate,
                                                   JwtProperties properties) {
        return redisTemplate
                .<AuthJwtSessionStore>map(template -> new AuthRedisJwtSessionStore(template, properties))
                .orElseGet(AuthInMemoryJwtSessionStore::new);
    }
}
