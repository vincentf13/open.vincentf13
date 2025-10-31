package open.vincentf13.exchange.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAggregate {
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
