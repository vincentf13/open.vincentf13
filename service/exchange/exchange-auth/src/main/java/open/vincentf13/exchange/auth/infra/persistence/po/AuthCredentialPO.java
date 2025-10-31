package open.vincentf13.exchange.auth.infra.persistence.po;

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
public class AuthCredentialPO {

    private Long id;
    private Long userId;
    private AuthCredentialType credentialType;
    private String secretHash;
    private String salt;
    private String status;
    private Instant expiresAt;
    private Instant createdAt;
}
