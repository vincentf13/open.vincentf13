package open.vincentf13.common.sdk.spring.security.token;

import java.time.Instant;

public record AuthTokenResponse(String token, Instant issuedAt, Instant expiresAt) {
}
