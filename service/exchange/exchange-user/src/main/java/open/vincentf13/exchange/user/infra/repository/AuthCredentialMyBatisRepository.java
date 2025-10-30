package open.vincentf13.exchange.user.infra.repository;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.user.domain.model.AuthCredential;
import open.vincentf13.exchange.user.domain.model.AuthCredentialType;
import open.vincentf13.exchange.user.domain.repository.AuthCredentialRepository;
import open.vincentf13.exchange.user.infra.mybatis.mapper.AuthCredentialMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AuthCredentialMyBatisRepository implements AuthCredentialRepository {

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
