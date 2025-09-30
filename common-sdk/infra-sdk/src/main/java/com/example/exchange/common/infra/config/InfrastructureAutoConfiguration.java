package com.example.exchange.common.infra.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Placeholder infrastructure auto-configuration for Redis, Kafka, DB, etc.
 */
@AutoConfiguration
public class InfrastructureAutoConfiguration {

    @Bean
    public RedisSerializer<?> redisKeySerializer() {
        return StringRedisSerializer.UTF_8;
    }
}
