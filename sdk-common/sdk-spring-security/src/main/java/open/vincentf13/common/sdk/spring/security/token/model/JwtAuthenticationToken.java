package open.vincentf13.common.sdk.spring.security.token.model;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.Collection;

/**
 * Access-token-backed Authentication that keeps track of the session id embedded in the JWT.
 */
public class JwtAuthenticationToken extends UsernamePasswordAuthenticationToken {

    private final String sessionId;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public JwtAuthenticationToken(String principal,
                                  String tokenValue,
                                  Collection<? extends GrantedAuthority> authorities,
                                  String sessionId,
                                  Instant issuedAt,
                                  Instant expiresAt) {
        super(principal, tokenValue, authorities);
        this.sessionId = sessionId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean hasSessionId() {
        return sessionId != null;
    }
}
