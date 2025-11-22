package open.vincentf13.exchange.user.infra.persistence.repository;

import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialType;
import open.vincentf13.exchange.user.domain.model.AuthCredentialPending;
import open.vincentf13.exchange.user.sdk.rest.api.dto.AuthCredentialPendingStatus;

import java.time.Instant;
import java.util.List;

public interface AuthCredentialPendingRepository {

    void insert(AuthCredentialPending credential);

    void markCompleted(Long userId, AuthCredentialType credentialType, Instant updatedAt);

    void markFailure(Long userId, AuthCredentialType credentialType, String lastError, Instant nextRetryAt, AuthCredentialPendingStatus status);

    List<AuthCredentialPending> findReady(int limit, Instant now);
}
