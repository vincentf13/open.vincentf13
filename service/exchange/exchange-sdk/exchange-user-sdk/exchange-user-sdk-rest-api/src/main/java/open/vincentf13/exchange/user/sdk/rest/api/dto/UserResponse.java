package open.vincentf13.exchange.user.sdk.rest.api.dto;

import open.vincentf13.exchange.user.sdk.rest.api.enums.UserStatus;

import java.time.Instant;

public record UserResponse(
        Long id,
        String externalId,
        String email,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
