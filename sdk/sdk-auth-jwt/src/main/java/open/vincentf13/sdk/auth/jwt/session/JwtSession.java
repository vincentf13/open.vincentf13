package open.vincentf13.sdk.auth.jwt.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Session metadata persisted alongside JWTs so services can enforce logout/kick-offline semantics.
 */
public class JwtSession {

    private String id;
    private String username;
    private Instant createdAt;
    private Instant refreshTokenExpiresAt;
    private List<String> authorities = new ArrayList<>();
    private Instant revokedAt;
    private String revokeReason;

    public JwtSession() {
        // for Jackson
    }

    public JwtSession(String id,
                      String username,
                      Instant createdAt,
                      Instant refreshTokenExpiresAt,
                      List<String> authorities) {
        this.id = Objects.requireNonNull(id, "session id");
        this.username = Objects.requireNonNull(username, "username");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.refreshTokenExpiresAt = Objects.requireNonNull(refreshTokenExpiresAt, "refreshTokenExpiresAt");
        if (authorities != null) {
            this.authorities = new ArrayList<>(authorities);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRefreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }

    public void setRefreshTokenExpiresAt(Instant refreshTokenExpiresAt) {
        this.refreshTokenExpiresAt = Objects.requireNonNull(refreshTokenExpiresAt, "refreshTokenExpiresAt");
    }

    public List<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(List<String> authorities) {
        this.authorities = authorities == null ? new ArrayList<>() : new ArrayList<>(authorities);
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public void setRevokeReason(String revokeReason) {
        this.revokeReason = revokeReason;
    }

    public void markRevoked(Instant when, String reason) {
        this.revokedAt = when;
        this.revokeReason = reason;
    }

    public boolean isActive(Instant now) {
        if (revokedAt != null) {
            return false;
        }
        return refreshTokenExpiresAt != null && refreshTokenExpiresAt.isAfter(now);
    }
}
