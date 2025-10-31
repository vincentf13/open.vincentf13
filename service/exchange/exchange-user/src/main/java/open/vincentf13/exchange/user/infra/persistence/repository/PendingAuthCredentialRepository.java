package open.vincentf13.exchange.user.infra.persistence.repository;

import open.vincentf13.exchange.auth.api.dto.AuthCredentialType;
import open.vincentf13.exchange.user.domain.model.PendingAuthCredential;
import open.vincentf13.exchange.user.domain.model.PendingAuthCredentialStatus;

import java.time.Instant;
import java.util.List;

public interface PendingAuthCredentialRepository {

    void insert(PendingAuthCredential credential);

    void markCompleted(Long userId, AuthCredentialType credentialType, Instant updatedAt);

    void markFailure(Long userId, AuthCredentialType credentialType, String lastError, Instant nextRetryAt, PendingAuthCredentialStatus status);

    List<PendingAuthCredential> findPending(int limit);
}
