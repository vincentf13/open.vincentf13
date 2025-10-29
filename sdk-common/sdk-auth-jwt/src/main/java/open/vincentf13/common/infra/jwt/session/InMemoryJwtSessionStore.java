package open.vincentf13.common.infra.jwt.session;

import java.util.Optional;

/**
 * Fallback in-memory store mainly intended for tests; data is not persisted across nodes.
 */
public class InMemoryJwtSessionStore implements JwtSessionStore {

    @Override
    public Optional<JwtSession> findById(String sessionId) {
        return Optional.empty();
    }
}
