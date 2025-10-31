package open.vincentf13.exchange.auth.api.dto;

import java.time.Instant;

public record AuthCredentialResponse(
        Long id,
        Long userId,
        AuthCredentialType credentialType,
        String status,
        Instant expiresAt,
        Instant createdAt
) { }
