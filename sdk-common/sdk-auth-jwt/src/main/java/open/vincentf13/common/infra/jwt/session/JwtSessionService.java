package open.vincentf13.common.infra.jwt.session;

import open.vincentf13.common.core.log.OpenLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Lightweight service exposing session validation helpers for downstream services.
 */
public class JwtSessionService {

    private static final Logger log = LoggerFactory.getLogger(JwtSessionService.class);

    private final JwtSessionStore sessionStore;

    public JwtSessionService(JwtSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    /**
     * Check whether the given session id is still active (exists and not revoked/expired).
     */
    public boolean isActive(String sessionId) {
        if (sessionId == null) {
            return true;
        }
        return sessionStore.findById(sessionId)
                .map(session -> session.isActive(Instant.now()))
                .orElse(false);
    }

    /**
     * Mark the session as revoked and remove it from the backing store. Intended for kick-offline flows.
     */
    public void revoke(String sessionId, String reason) {
        if (sessionId == null) {
            return;
        }
        sessionStore.markRevoked(sessionId, Instant.now(), reason);
        sessionStore.delete(sessionId);
        OpenLog.info(log,
                "JwtSessionRevoked",
                "Session revoked",
                "sessionId", sessionId,
                "reason", reason);
    }
}
