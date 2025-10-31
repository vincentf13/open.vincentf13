package open.vincentf13.exchange.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuthCredentialCreateRequest(
        @NotNull(message = "User id is required")
        Long userId,

        @NotNull(message = "Credential type is required")
        AuthCredentialType credentialType,

        @NotBlank(message = "Secret is required")
        @Size(max = 512, message = "Secret must not exceed 512 characters")
        String secret,

        @NotBlank(message = "Status is required")
        @Size(max = 32, message = "Status must not exceed 32 characters")
        String status
) { }
