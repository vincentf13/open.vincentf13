package open.vincentf13.exchange.user.infra.persistence.repository;

import open.vincentf13.exchange.user.api.dto.AuthCredentialType;
import open.vincentf13.exchange.user.domain.model.AuthCredential;

import java.util.Optional;

public interface AuthCredentialRepository {

    void insert(AuthCredential credential);

    Optional<AuthCredential> findByUserIdAndType(Long userId, AuthCredentialType credentialType);
}
