package open.vincentf13.exchange.auth.infra.persistence.repository;

import open.vincentf13.exchange.auth.domain.model.AuthCredential;

import java.util.List;
import java.util.Optional;

public interface AuthCredentialRepository {

    Long insertSelective(AuthCredential credential);

    void updateSelective(AuthCredential credential);

    Optional<AuthCredential> findOne(AuthCredential probe);

    List<AuthCredential> findBy(AuthCredential probe);

    void batchInsert(List<AuthCredential> credentials);

    void batchUpdate(List<AuthCredential> credentials);
}
