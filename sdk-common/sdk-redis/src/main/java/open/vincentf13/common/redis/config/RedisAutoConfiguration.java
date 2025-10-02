package open.vincentf13.common.redis.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis-related default auto-configuration (key serializers, common beans, etc.).
 */
@AutoConfiguration
public class RedisAutoConfiguration {

    @Bean
    public RedisSerializer<?> redisKeySerializer() {
        return StringRedisSerializer.UTF_8;
    }
}
