package open.vincentf13.sdk.auth.jwt.model;

import java.time.Instant;

public record RefreshTokenParseInfo(String tokenValue,
                                    String subject,
                                    String sessionId,
                                    Instant issuedAt,
                                    Instant expiresAt) {

    public boolean hasSessionId() {
        return sessionId != null;
    }
}
