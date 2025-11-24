package open.vincentf13.exchange.auth.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.auth.domain.model.AuthCredential;
import open.vincentf13.exchange.auth.infra.persistence.mapper.AuthCredentialMapper;
import open.vincentf13.exchange.auth.infra.persistence.po.AuthCredentialPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Validated
public class AuthCredentialRepository {

    private final AuthCredentialMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public Long insertSelective(@NotNull @Valid AuthCredential credential) {
        if (credential.getId() == null) {
            credential.setId(idGenerator.newLong());
        }
        AuthCredentialPO po = OpenMapstruct.map(credential, AuthCredentialPO.class);
        mapper.insertSelective(po);
        return credential.getId();
    }

    public boolean updateSelectiveBy(@NotNull @Valid AuthCredential update,
                                     @NotNull Long id,
                                     Long userId,
                                     Integer expectedVersion,
                                     String currentStatus) {
        AuthCredentialPO record = OpenMapstruct.map(update, AuthCredentialPO.class);
        return mapper.updateSelectiveBy(record, id, userId, expectedVersion, currentStatus) > 0;
    }

    public Optional<AuthCredential> findOne(@NotNull AuthCredential probe) {
        List<AuthCredential> results = findBy(probe);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single credential but found " + results.size());
        }
        return Optional.of(results.get(0));
    }

    public List<AuthCredential> findBy(@NotNull AuthCredential probe) {
        AuthCredentialPO poProbe = OpenMapstruct.map(probe, AuthCredentialPO.class);
        return mapper.findBy(poProbe).stream()
                .map(item -> OpenMapstruct.map(item, AuthCredential.class))
                .collect(Collectors.toList());
    }

    public void batchInsert(@NotEmpty List<AuthCredential> credentials) {
        credentials.stream()
                .filter(credential -> credential.getId() == null)
                .forEach(credential -> credential.setId(idGenerator.newLong()));
        mapper.batchInsert(credentials.stream()
                .map(credential -> OpenMapstruct.map(credential, AuthCredentialPO.class))
                .collect(Collectors.toList()));
    }
}
