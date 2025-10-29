package open.vincentf13.common.infra.jwt.session;

import java.time.Instant;
import java.util.Optional;

public interface JwtSessionStore {

    void save(JwtSession session);

    Optional<JwtSession> findById(String sessionId);

    void delete(String sessionId);

    void markRevoked(String sessionId, Instant revokedAt, String reason);
}
