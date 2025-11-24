package open.vincentf13.exchange.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import open.vincentf13.exchange.user.sdk.rest.api.enums.UserStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String externalId;
    @NotBlank
    @Email
    private String email;
    @NotNull
    private UserStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isActive() {
        return UserStatus.ACTIVE.equals(status);
    }

    public static User createActive(String rawEmail, String externalId) {
        String normalizedEmail = normalizeEmail(rawEmail);
        return User.builder()
                .email(normalizedEmail)
                .externalId(externalId != null ? externalId : UUID.randomUUID().toString())
                .status(UserStatus.ACTIVE)
                .build();
    }

    public static String normalizeEmail(String rawEmail) {
        Objects.requireNonNull(rawEmail, "email must not be null");
        return rawEmail.toLowerCase();
    }
}
