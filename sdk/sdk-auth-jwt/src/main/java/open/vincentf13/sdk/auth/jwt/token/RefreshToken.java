package open.vincentf13.sdk.auth.jwt.token;

import java.time.Instant;

public record RefreshToken(String tokenValue,
                           String subject,
                           String sessionId,
                           Instant issuedAt,
                           Instant expiresAt) {
    
    public boolean hasSessionId() {
        return sessionId != null;
    }
}
