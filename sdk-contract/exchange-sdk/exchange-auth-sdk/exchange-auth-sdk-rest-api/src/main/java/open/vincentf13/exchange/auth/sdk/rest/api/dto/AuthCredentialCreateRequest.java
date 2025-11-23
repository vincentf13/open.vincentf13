package open.vincentf13.exchange.auth.sdk.rest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import open.vincentf13.exchange.auth.sdk.rest.api.enums.AuthCredentialType;

public record AuthCredentialCreateRequest(
        @NotNull(message = "User id is required")
        Long userId,

        @NotNull(message = "Credential type is required")
        AuthCredentialType credentialType,

        @NotBlank(message = "Secret hash is required")
        @Size(max = 512, message = "Secret hash must not exceed 512 characters")
        String secretHash,

        @NotBlank(message = "Salt is required")
        @Size(max = 128, message = "Salt must not exceed 128 characters")
        String salt,

        @NotBlank(message = "Status is required")
        @Size(max = 32, message = "Status must not exceed 32 characters")
        String status
) { }
