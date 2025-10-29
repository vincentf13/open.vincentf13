package open.vincentf13.common.infra.jwt.session;

import open.vincentf13.common.infra.jwt.token.JwtProperties;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Redis-backed session store so multiple services/pods can enforce shared logout state.
 */
public class RedisJwtSessionStore implements JwtSessionStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtProperties properties;

    public RedisJwtSessionStore(RedisTemplate<String, Object> redisTemplate,
                                JwtProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void save(JwtSession session) {
        String key = buildKey(session.getId());
        Duration ttl = Duration.between(Instant.now(), session.getRefreshTokenExpiresAt());
        if (ttl.isNegative() || ttl.isZero()) {
            redisTemplate.delete(key);
            return;
        }
        redisTemplate.opsForValue().set(key, session, ttl);
    }

    @Override
    public Optional<JwtSession> findById(String sessionId) {
        Object value = redisTemplate.opsForValue().get(buildKey(sessionId));
        if (value instanceof JwtSession jwtSession) {
            return Optional.of(jwtSession);
        }
        return Optional.empty();
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
