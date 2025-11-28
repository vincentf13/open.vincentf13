package open.vincentf13.sdk.auth.jwt.session;

import java.time.Instant;
import java.util.Optional;

/*
  Read-only helper exposing session lookups for downstream services.
 */
public class JwtSessionService {

    private final JwtSessionStore sessionStore;

    public JwtSessionService(JwtSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public boolean isActive(String sessionId) {
        if (sessionId == null) {
            return true;
        }
        return sessionStore.findById(sessionId)
                .map(session -> session.isActive(Instant.now()))
                .orElse(false);
    }

    public Optional<JwtSession> findById(String sessionId) {
        return sessionStore.findById(sessionId);
    }
}
