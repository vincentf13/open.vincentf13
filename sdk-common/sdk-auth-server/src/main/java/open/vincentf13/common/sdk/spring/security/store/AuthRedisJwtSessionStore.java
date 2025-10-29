package open.vincentf13.common.sdk.spring.security.store;

import open.vincentf13.common.core.log.OpenLog;
import open.vincentf13.common.infra.jwt.session.JwtSession;
import open.vincentf13.common.infra.jwt.token.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class AuthRedisJwtSessionStore implements AuthJwtSessionStore {

    private static final Logger log = LoggerFactory.getLogger(AuthRedisJwtSessionStore.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtProperties properties;

    public AuthRedisJwtSessionStore(RedisTemplate<String, Object> redisTemplate,
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
            OpenLog.info(log,
                    "RedisSessionRevoked",
                    "Session revoked in redis",
                    "sessionId", sessionId,
                    "reason", reason);
        });
    }

    private String buildKey(String sessionId) {
        return properties.getSessionStorePrefix() + ':' + sessionId;
    }
}
