package open.vincentf13.sdk.auth.server.store;

import open.vincentf13.sdk.auth.jwt.session.JwtSession;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionStore;

import java.time.Instant;

public interface AuthJwtSessionStore extends JwtSessionStore {

    void save(JwtSession session);

    void delete(String sessionId);

    void markRevoked(String sessionId, Instant revokedAt, String reason);
}
