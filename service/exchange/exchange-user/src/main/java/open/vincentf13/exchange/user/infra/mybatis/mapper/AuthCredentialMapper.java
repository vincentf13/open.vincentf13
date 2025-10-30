package open.vincentf13.exchange.user.infra.mybatis.mapper;

import open.vincentf13.exchange.user.domain.model.AuthCredential;
import open.vincentf13.exchange.user.domain.model.AuthCredentialType;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

public interface AuthCredentialMapper {

    void insert(AuthCredential credential);

    Optional<AuthCredential> findByUserIdAndType(@Param("userId") Long userId,
                                                 @Param("credentialType") AuthCredentialType credentialType);
}
