package open.vincentf13.exchange.auth.sdk.rest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import open.vincentf13.exchange.auth.sdk.rest.api.enums.AuthCredentialType;

public record AuthCredentialPrepareRequest(
    @NotNull(message = "Credential type is required") AuthCredentialType credentialType,
    @NotBlank(message = "Secret is required")
        @Size(max = 512, message = "Secret must not exceed 512 characters")
        String secret) {}
