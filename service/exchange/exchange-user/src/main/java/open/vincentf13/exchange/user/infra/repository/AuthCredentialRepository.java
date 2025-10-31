package open.vincentf13.exchange.user.infra.repository;

import open.vincentf13.exchange.user.domain.AuthCredential;
import open.vincentf13.exchange.user.domain.AuthCredentialType;

import java.util.Optional;

public interface AuthCredentialRepository {

    void insert(AuthCredential credential);

    Optional<AuthCredential> findByUserIdAndType(Long userId, AuthCredentialType credentialType);
}
