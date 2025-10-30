package open.vincentf13.exchange.user.domain.model;

import java.time.Instant;

public class AuthCredential {

    private Long id;
    private Long userId;
    private AuthCredentialType credentialType;
    private String secretHash;
    private String salt;
    private String status;
    private Instant expiresAt;
    private Instant createdAt;

    public AuthCredential() {
    }

    public AuthCredential(Long id,
                          Long userId,
                          AuthCredentialType credentialType,
                          String secretHash,
                          String salt,
                          String status,
                          Instant expiresAt,
                          Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.credentialType = credentialType;
        this.secretHash = secretHash;
        this.salt = salt;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public AuthCredentialType getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(AuthCredentialType credentialType) {
        this.credentialType = credentialType;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
