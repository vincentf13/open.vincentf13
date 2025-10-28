package open.vincentf13.common.sdk.spring.security.token;

import java.time.Instant;

public record JwtResponse(String token, Instant issuedAt, Instant expiresAt) {
}
