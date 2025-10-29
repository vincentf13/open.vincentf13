package open.vincentf13.common.sdk.spring.security.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback in-memory store so single-node services can try the session features without Redis.
 */
@Component
@ConditionalOnMissingBean(JwtSessionStore.class)
public class InMemoryJwtSessionStore implements JwtSessionStore {

    private final Map<String, JwtSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(JwtSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    public Optional<JwtSession> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public void delete(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public void markRevoked(String sessionId, Instant revokedAt, String reason) {
        findById(sessionId).ifPresent(session -> session.markRevoked(revokedAt, reason));
    }
}
