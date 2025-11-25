package open.vincentf13.exchange.user.infra.persistence.repository;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.auth.sdk.rest.api.enums.AuthCredentialType;
import open.vincentf13.exchange.user.domain.model.AuthCredentialPending;
import open.vincentf13.exchange.user.infra.persistence.mapper.AuthCredentialPendingMapper;
import open.vincentf13.exchange.user.infra.persistence.po.AuthCredentialPendingPO;
import open.vincentf13.exchange.user.sdk.rest.api.enums.AuthCredentialPendingStatus;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Validated
public class AuthCredentialPendingRepository {

    private final AuthCredentialPendingMapper mapper;

    public void insert(@NotNull AuthCredentialPending credential) {
        mapper.insert(OpenObjectMapper.convert(credential, AuthCredentialPendingPO.class));
    }

    public void markCompleted(@NotNull Long userId, @NotNull AuthCredentialType credentialType, @NotNull Instant updatedAt) {
        mapper.markCompleted(userId, credentialType, AuthCredentialPendingStatus.COMPLETED.name(), updatedAt);
    }

    public void markFailure(@NotNull Long userId,
                            @NotNull AuthCredentialType credentialType,
                            String lastError,
                            Instant nextRetryAt,
                            @NotNull AuthCredentialPendingStatus status) {
        mapper.markFailure(userId, credentialType, status.name(), lastError, nextRetryAt, Instant.now());
    }

    public List<AuthCredentialPending> findReady(int limit, @NotNull Instant now) {
        return mapper.findReady(AuthCredentialPendingStatus.PENDING.name(), now, limit).stream()
                .map(item -> OpenObjectMapper.convert(item, AuthCredentialPending.class))
                .collect(Collectors.toList());
    }
}
