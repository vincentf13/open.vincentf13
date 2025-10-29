package open.vincentf13.common.infra.jwt.token.model;

import java.time.Instant;

public record RefreshTokenClaims(String tokenValue,
                                 String subject,
                                 String sessionId,
                                 Instant issuedAt,
                                 Instant expiresAt) {

    public boolean hasSessionId() {
        return sessionId != null;
    }
}
