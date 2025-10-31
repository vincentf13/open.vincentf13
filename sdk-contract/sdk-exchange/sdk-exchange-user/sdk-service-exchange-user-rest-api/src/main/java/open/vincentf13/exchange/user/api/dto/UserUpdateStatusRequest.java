package open.vincentf13.exchange.user.api.dto;

import jakarta.validation.constraints.NotNull;

public record UserUpdateStatusRequest(
        @NotNull(message = "Status is required")
        UserStatus status
) { }
