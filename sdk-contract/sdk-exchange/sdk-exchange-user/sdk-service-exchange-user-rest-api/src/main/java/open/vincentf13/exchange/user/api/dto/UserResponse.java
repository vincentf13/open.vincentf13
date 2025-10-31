package open.vincentf13.exchange.user.api.dto;

import open.vincentf13.exchange.user.domain.model.UserStatus;

import java.time.Instant;

public record UserResponse(
        Long id,
        String externalId,
        String email,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) { }
