package open.vincentf13.sdk.auth.server.controller.dto;

import java.time.Instant;

public record JwtTokenPair(String jwtToken,
                           Instant issuedAt,
                           Instant expiresAt,
                           String refreshToken,
                           Instant refreshTokenExpiresAt,
                           String sessionId) {
}
