package open.vincentf13.exchange.auth.sdk.rest.api.dto;

import java.time.Instant;

public record AuthCredentialResponse(
        Long id,
        Long userId,
        AuthCredentialType credentialType,
        String status,
        Instant expiresAt,
        Instant createdAt
) { }
