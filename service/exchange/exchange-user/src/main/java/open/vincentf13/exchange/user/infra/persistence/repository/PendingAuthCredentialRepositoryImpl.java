package open.vincentf13.exchange.user.infra.persistence.repository;

import lombok.RequiredArgsConstructor;
import open.vincentf13.common.core.OpenMapstruct;
import open.vincentf13.exchange.auth.api.dto.AuthCredentialType;
import open.vincentf13.exchange.user.domain.model.PendingAuthCredential;
import open.vincentf13.exchange.user.domain.model.PendingAuthCredentialStatus;
import open.vincentf13.exchange.user.infra.persistence.mapper.PendingAuthCredentialMapper;
import open.vincentf13.exchange.user.infra.persistence.po.PendingAuthCredentialPO;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PendingAuthCredentialRepositoryImpl implements PendingAuthCredentialRepository {

    private final PendingAuthCredentialMapper mapper;

    @Override
    public void insert(PendingAuthCredential credential) {
        mapper.insert(OpenMapstruct.map(credential, PendingAuthCredentialPO.class));
    }

    @Override
    public void markCompleted(Long userId, AuthCredentialType credentialType, Instant updatedAt) {
        mapper.markCompleted(userId, credentialType, PendingAuthCredentialStatus.COMPLETED.name(), updatedAt);
    }

    @Override
    public void markFailure(Long userId,
                            AuthCredentialType credentialType,
                            String lastError,
                            Instant nextRetryAt,
                            PendingAuthCredentialStatus status) {
        mapper.markFailure(userId, credentialType, status.name(), lastError, nextRetryAt, Instant.now());
    }

    @Override
    public List<PendingAuthCredential> findPending(int limit) {
        return mapper.findByStatus(PendingAuthCredentialStatus.PENDING.name(), limit).stream()
                .map(item -> OpenMapstruct.map(item, PendingAuthCredential.class))
                .collect(Collectors.toList());
    }
}
