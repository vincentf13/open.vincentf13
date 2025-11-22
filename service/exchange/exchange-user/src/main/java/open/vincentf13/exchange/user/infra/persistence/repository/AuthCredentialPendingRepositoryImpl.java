package open.vincentf13.exchange.user.infra.persistence.repository;

import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.core.OpenMapstruct;
import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialType;
import open.vincentf13.exchange.user.domain.model.AuthCredentialPending;
import open.vincentf13.exchange.user.sdk.rest.api.dto.AuthCredentialPendingStatus;
import open.vincentf13.exchange.user.infra.persistence.mapper.AuthCredentialPendingMapper;
import open.vincentf13.exchange.user.infra.persistence.po.AuthCredentialPendingPO;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class AuthCredentialPendingRepositoryImpl implements AuthCredentialPendingRepository {

    private final AuthCredentialPendingMapper mapper;

    @Override
    public void insert(AuthCredentialPending credential) {
        mapper.insert(OpenMapstruct.map(credential, AuthCredentialPendingPO.class));
    }

    @Override
    public void markCompleted(Long userId, AuthCredentialType credentialType, Instant updatedAt) {
        mapper.markCompleted(userId, credentialType, AuthCredentialPendingStatus.COMPLETED.name(), updatedAt);
    }

    @Override
    public void markFailure(Long userId,
                            AuthCredentialType credentialType,
                            String lastError,
                            Instant nextRetryAt,
                            AuthCredentialPendingStatus status) {
        mapper.markFailure(userId, credentialType, status.name(), lastError, nextRetryAt, Instant.now());
    }

    @Override
    public List<AuthCredentialPending> findReady(int limit, Instant now) {
        return mapper.findReady(AuthCredentialPendingStatus.PENDING.name(), now, limit).stream()
                .map(item -> OpenMapstruct.map(item, AuthCredentialPending.class))
                .collect(Collectors.toList());
    }
}
