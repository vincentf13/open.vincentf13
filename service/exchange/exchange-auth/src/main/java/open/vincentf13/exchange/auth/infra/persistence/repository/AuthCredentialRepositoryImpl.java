package open.vincentf13.exchange.auth.infra.persistence.repository;

import lombok.RequiredArgsConstructor;
import open.vincentf13.common.core.OpenMapstruct;
import open.vincentf13.exchange.auth.domain.model.AuthCredential;
import open.vincentf13.exchange.auth.infra.persistence.mapper.AuthCredentialMapper;
import open.vincentf13.exchange.auth.infra.persistence.po.AuthCredentialPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class AuthCredentialRepositoryImpl implements AuthCredentialRepository {

    private final AuthCredentialMapper mapper;

    @Override
    public void insertSelective(AuthCredential credential) {
        mapper.insertSelective(OpenMapstruct.map(credential, AuthCredentialPO.class));
    }

    @Override
    public void updateSelective(AuthCredential credential) {
        mapper.updateSelective(OpenMapstruct.map(credential, AuthCredentialPO.class));
    }

    @Override
    public Optional<AuthCredential> findOne(AuthCredential probe) {
        List<AuthCredential> results = findBy(probe);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single credential but found " + results.size());
        }
        return Optional.of(results.get(0));
    }

    @Override
    public List<AuthCredential> findBy(AuthCredential probe) {
        AuthCredentialPO poProbe = OpenMapstruct.map(probe, AuthCredentialPO.class);
        return mapper.findByPO(poProbe).stream()
                .map(item -> OpenMapstruct.map(item, AuthCredential.class))
                .collect(Collectors.toList());
    }

    @Override
    public void batchInsert(List<AuthCredential> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return;
        }
        mapper.batchInsert(credentials.stream()
                .map(credential -> OpenMapstruct.map(credential, AuthCredentialPO.class))
                .collect(Collectors.toList()));
    }

    @Override
    public void batchUpdate(List<AuthCredential> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return;
        }
        mapper.batchUpdate(credentials.stream()
                .map(credential -> OpenMapstruct.map(credential, AuthCredentialPO.class))
                .collect(Collectors.toList()));
    }
}
