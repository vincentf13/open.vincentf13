package open.vincentf13.exchange.user.infra.repository;

import open.vincentf13.exchange.user.domain.model.AuthCredential;
import open.vincentf13.exchange.user.domain.model.AuthCredentialType;
import open.vincentf13.exchange.user.domain.repository.AuthCredentialRepository;
import open.vincentf13.exchange.user.infra.mybatis.mapper.AuthCredentialMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AuthCredentialMyBatisRepository implements AuthCredentialRepository {

    private final AuthCredentialMapper mapper;

    public AuthCredentialMyBatisRepository(AuthCredentialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(AuthCredential credential) {
        mapper.insert(credential);
    }

    @Override
    public Optional<AuthCredential> findByUserIdAndType(Long userId, AuthCredentialType type) {
        return mapper.findByUserIdAndType(userId, type);
    }
}
