package open.vincentf13.exchange.user.dto;

import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.user.domain.model.UserStatus;

public record UpdateUserStatusRequest(
        @NotNull(message = "Status is required")
        UserStatus status
) { }
