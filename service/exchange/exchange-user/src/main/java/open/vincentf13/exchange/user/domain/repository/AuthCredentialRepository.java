package open.vincentf13.exchange.user.domain.repository;

import open.vincentf13.exchange.user.domain.model.AuthCredential;
import open.vincentf13.exchange.user.domain.model.AuthCredentialType;

import java.util.Optional;

public interface AuthCredentialRepository {

    void insert(AuthCredential credential);

    Optional<AuthCredential> findByUserIdAndType(Long userId, AuthCredentialType credentialType);
}
