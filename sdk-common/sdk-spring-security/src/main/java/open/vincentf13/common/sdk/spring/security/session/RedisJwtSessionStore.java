package open.vincentf13.common.sdk.spring.security.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import open.vincentf13.common.core.log.OpenLog;
import open.vincentf13.common.sdk.spring.security.token.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Redis-backed session store so multiple services/pods can enforce shared logout state.
 */
@Component
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisJwtSessionStore implements JwtSessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisJwtSessionStore.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JwtProperties properties;

    public RedisJwtSessionStore(StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                JwtProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void save(JwtSession session) {
        try {
            String key = buildKey(session.getId());
            String json = objectMapper.writeValueAsString(session);
            Duration ttl = Duration.between(Instant.now(), session.getRefreshTokenExpiresAt());
            if (ttl.isNegative() || ttl.isZero()) {
                redisTemplate.delete(key);
                return;
            }
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException ex) {
            OpenLog.warn(log, "JwtSessionSerializationFailed", "Failed to serialize session", ex, "sessionId", session.getId());
        }
    }

    @Override
    public Optional<JwtSession> findById(String sessionId) {
        String key = buildKey(sessionId);
        return Optional.ofNullable(redisTemplate.opsForValue().get(key))
                .flatMap(value -> {
                    try {
                        return Optional.of(objectMapper.readValue(value, JwtSession.class));
                    } catch (JsonProcessingException ex) {
                        OpenLog.warn(log, "JwtSessionDeserializationFailed", "Failed to deserialize session", ex, "sessionId", sessionId);
                        return Optional.empty();
                    }
                });
    }

    @Override
    public void delete(String sessionId) {
        redisTemplate.delete(buildKey(sessionId));
    }

    @Override
    public void markRevoked(String sessionId, Instant revokedAt, String reason) {
        findById(sessionId).ifPresent(session -> {
            session.markRevoked(revokedAt, reason);
            save(session);
        });
    }

    private String buildKey(String sessionId) {
        return properties.getSessionStorePrefix() + ':' + sessionId;
    }
}
