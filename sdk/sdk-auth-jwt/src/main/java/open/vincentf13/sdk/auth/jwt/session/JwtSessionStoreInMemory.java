package open.vincentf13.sdk.auth.jwt.session;

import open.vincentf13.sdk.core.log.OpenLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback in-memory store mainly intended for tests; data is not persisted across nodes.
 */
public class JwtSessionStoreInMemory implements JwtSessionStore {

    private static final Logger log = LoggerFactory.getLogger(JwtSessionStoreInMemory.class);

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
        findById(sessionId).ifPresent(session -> {
            session.markRevoked(revokedAt, reason);
            sessions.put(sessionId, session);
            OpenLog.info(log,
                    "InMemorySessionRevoked",
                    "Session revoked in memory",
                    "sessionId", sessionId,
                    "reason", reason);
        });
    }
}
