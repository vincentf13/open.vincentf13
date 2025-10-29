package open.vincentf13.common.infra.jwt.session;

import java.util.Optional;

/**
 * Read-only abstraction allowing services to query session metadata.
 */
public interface JwtSessionStore {

    Optional<JwtSession> findById(String sessionId);
}
