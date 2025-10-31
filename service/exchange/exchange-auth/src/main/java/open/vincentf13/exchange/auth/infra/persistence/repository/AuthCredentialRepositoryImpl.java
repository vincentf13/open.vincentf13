package open.vincentf13.exchange.auth.infra.persistence.repository;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.auth.domain.model.AuthCredential;
import open.vincentf13.exchange.auth.infra.persistence.mapper.AuthCredentialMapper;
import open.vincentf13.exchange.user.api.dto.AuthCredentialType;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AuthCredentialRepositoryImpl implements AuthCredentialRepository {

    private final AuthCredentialMapper mapper;

    @Override
    public void insert(AuthCredential credential) {
        mapper.insert(credential);
    }

    @Override
    public Optional<AuthCredential> findByUserIdAndType(Long userId, AuthCredentialType credentialType) {
        return Optional.ofNullable(mapper.findByUserIdAndType(userId, credentialType));
    }
}
