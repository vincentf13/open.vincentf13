package open.vincentf13.sdk.auth.jwt.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import open.vincentf13.sdk.auth.jwt.JwtEvent;
import open.vincentf13.sdk.auth.jwt.token.config.JwtProperties;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.mapper.OpenObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;

/** Redis-backed session store supporting read and write operations. */
public class JwtSessionStoreRedis implements JwtSessionStore {

  private final RedisTemplate<String, Object> redisTemplate;
  private final JwtProperties properties;

  public JwtSessionStoreRedis(
      RedisTemplate<String, Object> redisTemplate, JwtProperties properties) {
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
    findById(sessionId)
        .ifPresent(
            session -> {
              session.markRevoked(revokedAt, reason);
              save(session);
              OpenLog.info(
                  JwtEvent.REDIS_SESSION_REVOKED, "sessionId", sessionId, "reason", reason);
            });
  }

  private String buildKey(String sessionId) {
    return properties.getSessionStorePrefix() + ':' + sessionId;
  }
}
