package open.vincentf13.exchange.auth.infra.persistence.repository;

import open.vincentf13.exchange.auth.domain.model.AuthCredential;
import open.vincentf13.exchange.user.api.dto.AuthCredentialType;

import java.util.Optional;

public interface AuthCredentialRepository {

    void insert(AuthCredential credential);

    Optional<AuthCredential> findByUserIdAndType(Long userId, AuthCredentialType credentialType);
}
