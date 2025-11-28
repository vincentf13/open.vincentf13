package open.vincentf13.exchange.auth.domain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.auth.sdk.rest.api.enums.AuthCredentialType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthCredential {
    private Long id;
    @NotNull
    private Long userId;
    @NotNull
    private AuthCredentialType credentialType;
    @NotBlank
    private String secretHash;
    @NotBlank
    private String salt;
    @NotBlank
    private String status;
    private Instant expiresAt;
    private Instant createdAt;
    
    public static AuthCredential create(Long userId,
                                        AuthCredentialType credentialType,
                                        String secretHash,
                                        String salt,
                                        String status) {
        return AuthCredential.builder()
                             .userId(userId)
                             .credentialType(credentialType)
                             .secretHash(secretHash)
                             .salt(salt)
                             .status(status)
                             .build();
    }
}
