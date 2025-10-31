package open.vincentf13.exchange.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingAuthCredential {

    private Long id;
    private Long userId;
    private AuthCredentialType credentialType;
    private String secretHash;
    private String salt;
    private PendingAuthCredentialStatus status;
    private Integer retryCount;
    private Instant nextRetryAt;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
}
