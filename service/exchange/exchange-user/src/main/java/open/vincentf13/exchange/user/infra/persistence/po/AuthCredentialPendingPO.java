package open.vincentf13.exchange.user.infra.persistence.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialType;
import open.vincentf13.exchange.user.sdk.rest.api.dto.AuthCredentialPendingStatus;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthCredentialPendingPO {

    private Long id;
    private Long userId;
    private AuthCredentialType credentialType;
    private String secretHash;
    private String salt;
    private AuthCredentialPendingStatus status;
    private Integer retryCount;
    private Instant nextRetryAt;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
}
