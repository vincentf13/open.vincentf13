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
public class AuthCredential {
    private Long id;
    private Long userId;
    private AuthCredentialType credentialType;
    private String secretHash;
    private String salt;
    private String status;
    private Instant expiresAt;
    private Instant createdAt;
}
