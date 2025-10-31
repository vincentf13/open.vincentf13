package open.vincentf13.exchange.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuthCredentialPrepareRequest(
        @NotNull(message = "Credential type is required")
        AuthCredentialType credentialType,

        @NotBlank(message = "Secret is required")
        @Size(max = 512, message = "Secret must not exceed 512 characters")
        String secret
) { }
