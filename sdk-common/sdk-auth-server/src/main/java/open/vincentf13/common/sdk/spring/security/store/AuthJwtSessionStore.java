package open.vincentf13.common.sdk.spring.security.store;

import open.vincentf13.common.infra.jwt.session.JwtSession;
import open.vincentf13.common.infra.jwt.session.JwtSessionStore;

import java.time.Instant;

public interface AuthJwtSessionStore extends JwtSessionStore {

    void save(JwtSession session);

    void delete(String sessionId);

    void markRevoked(String sessionId, Instant revokedAt, String reason);
}
