package open.vincentf13.common.sdk.spring.security.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import open.vincentf13.common.core.OpenConstant;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "open.vincentf13.security.jwt")
public class JwtProperties {

    /** Shared secret used for HMAC signing (minimum 32 characters). */
    @NotBlank
    @Size(min = 32)
    private String secret = "change-me-change-me-change-me-change";

    /** Issuer claim placed in generated tokens. */
    private String issuer = OpenConstant.BASE_PACKAGE;

    /** Access token lifetime in seconds. */
    private long accessTokenTtlSeconds = 3600;

    /** Refresh token lifetime in seconds. */
    private long refreshTokenTtlSeconds = 604800;

    /** Redis key prefix (or general namespace) for session metadata. */
    private String sessionStorePrefix = "open:sessions";

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
}
