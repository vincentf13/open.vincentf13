package open.vincentf13.sdk.auth.jwt.session;

import java.time.Instant;
import java.util.Optional;

/**
 * Read-only abstraction allowing services to query session metadata.
 */
public interface JwtSessionStore {

    Optional<JwtSession> findById(String sessionId);

    default void save(JwtSession session) {
        throw new UnsupportedOperationException("Save operation is not supported by this store");
    }

    default void delete(String sessionId) {
        throw new UnsupportedOperationException("Delete operation is not supported by this store");
    }

    default void markRevoked(String sessionId, Instant revokedAt, String reason) {
        throw new UnsupportedOperationException("Mark revoked operation is not supported by this store");
    }
}
