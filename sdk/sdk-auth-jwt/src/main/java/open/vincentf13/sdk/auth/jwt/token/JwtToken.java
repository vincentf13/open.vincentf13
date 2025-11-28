package open.vincentf13.sdk.auth.jwt.token;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.Collection;

/**
 Access-jwtToken-backed Authentication that keeps track of the session id embedded in the JWT.
 */
public class JwtToken extends UsernamePasswordAuthenticationToken {
    
    private final String sessionId;
    private final Instant issuedAt;
    private final Instant expiresAt;
    
    public JwtToken(Object principal,
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
