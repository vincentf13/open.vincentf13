package open.vincentf13.exchange.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import open.vincentf13.exchange.user.sdk.rest.api.dto.UserStatus;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String externalId;
    private String email;
    private UserStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isActive() {
        return UserStatus.ACTIVE.equals(status);
    }
}
