package open.vincentf13.exchange.auth.infra.persistence.mapper;

import open.vincentf13.exchange.user.api.dto.AuthCredentialType;
import open.vincentf13.exchange.auth.domain.model.AuthCredential;
import org.apache.ibatis.annotations.Param;

public interface AuthCredentialMapper {

    void insert(AuthCredential credential);

    AuthCredential findByUserIdAndType(@Param("userId") Long userId,
                                       @Param("credentialType") AuthCredentialType credentialType);
}
