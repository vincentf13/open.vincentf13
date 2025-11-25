package open.vincentf13.sdk.auth.jwt.session;

import open.vincentf13.sdk.auth.jwt.config.JwtProperties;
import open.vincentf13.sdk.auth.jwt.JwtEventEnum;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.core.log.OpenLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Redis-backed session store supporting read and write operations.
 */
public class JwtSessionStoreRedis implements JwtSessionStore {

    private static final Logger log = LoggerFactory.getLogger(JwtSessionStoreRedis.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtProperties properties;

    public JwtSessionStoreRedis(RedisTemplate<String, Object> redisTemplate,
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
        if (value == null) {
            return Optional.empty();
        }

        JwtSession session = null;

        if (value instanceof JwtSession j) {
            session = j;
        } else {
            session = OpenObjectMapper.convert(value, JwtSession.class);
        }
        return Optional.ofNullable(session);
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
            OpenLog.info(log, JwtEventEnum.REDIS_SESSION_REVOKED,
                         "sessionId", sessionId,
                         "reason", reason);
        });
    }

    private String buildKey(String sessionId) {
        return properties.getSessionStorePrefix() + ':' + sessionId;
    }
}
