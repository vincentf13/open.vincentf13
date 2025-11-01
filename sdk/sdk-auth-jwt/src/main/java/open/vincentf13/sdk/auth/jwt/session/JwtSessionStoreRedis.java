package open.vincentf13.sdk.auth.jwt.session;

import open.vincentf13.sdk.auth.jwt.token.JwtProperties;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;

/**
 * Redis-backed store used for read operations (issue/refresh handled by auth server).
 */
public class JwtSessionStoreRedis implements JwtSessionStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtProperties properties;

    public JwtSessionStoreRedis(RedisTemplate<String, Object> redisTemplate,
                                JwtProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public Optional<JwtSession> findById(String sessionId) {
        Object value = redisTemplate.opsForValue().get(buildKey(sessionId));
        if (value instanceof JwtSession jwtSession) {
            return Optional.of(jwtSession);
        }
        return Optional.empty();
    }

    private String buildKey(String sessionId) {
        return properties.getSessionStorePrefix() + ':' + sessionId;
    }
}
