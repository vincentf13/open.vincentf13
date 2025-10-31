package open.vincentf13.exchange.user.infra.persistence.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.user.domain.model.UserStatus;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPO {

    private Long id;
    private String externalId;
    private String email;
    private UserStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
