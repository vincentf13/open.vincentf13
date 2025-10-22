package open.vincentf13.common.sdk.spring.security.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    private String issuer = "open.vincentf13";

    /** Access token lifetime in seconds. */
    private long accessTokenTtlSeconds = 3600;

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
}
