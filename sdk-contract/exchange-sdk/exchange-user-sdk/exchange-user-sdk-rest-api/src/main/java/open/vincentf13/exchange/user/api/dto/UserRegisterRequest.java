package open.vincentf13.exchange.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegisterRequest(
        @Email(message = "Email format invalid")
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password length must be between 8 and 128 characters")
        String password,

        @Size(max = 64, message = "External id max length is 64")
        String externalId
) { }
