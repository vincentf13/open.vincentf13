package open.vincentf13.sdk.auth.jwt.token.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import open.vincentf13.sdk.core.OpenConstant;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "open.vincentf13.security.jwt")
public class JwtProperties {

  @NotBlank
  @Size(min = 32)
  private String secret = "change-me-change-me-change-me-change";

  private String issuer = OpenConstant.Package.Names.BASE_PACKAGE;

  private long accessTokenTtlSeconds = 3600;

  private long refreshTokenTtlSeconds = 604800;

  private String sessionStorePrefix = "open:vincentf13:security:sessions";

  private boolean checkSessionActive = false;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public long getAccessTokenTtlSeconds() {
    return accessTokenTtlSeconds;
  }

  public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
    this.accessTokenTtlSeconds = accessTokenTtlSeconds;
  }

  public long getRefreshTokenTtlSeconds() {
    return refreshTokenTtlSeconds;
  }

  public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
    this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
  }

  public String getSessionStorePrefix() {
    return sessionStorePrefix;
  }

  public void setSessionStorePrefix(String sessionStorePrefix) {
    this.sessionStorePrefix = sessionStorePrefix;
  }

  public boolean isCheckSessionActive() {
    return checkSessionActive;
  }

  public void setCheckSessionActive(boolean checkSessionActive) {
    this.checkSessionActive = checkSessionActive;
  }
}
