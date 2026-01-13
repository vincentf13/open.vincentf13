package open.vincentf13.exchange.user.infra.retry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.auth.sdk.rest.api.enums.AuthCredentialType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthCredentialCreatePayload {
  private Long userId;
  private AuthCredentialType credentialType;
  private String secretHash;
  private String salt;
}
