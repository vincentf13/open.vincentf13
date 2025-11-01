package open.vincentf13.sdk.auth.jwt.token;

import java.time.Instant;

public record JwtResponse(String token,
                          Instant issuedAt,
                          Instant expiresAt,
                          String refreshToken,
                          Instant refreshTokenExpiresAt,
                          String sessionId) {
}
