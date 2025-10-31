package open.vincentf13.exchange.user.infra.mybatis.mapper;

import open.vincentf13.exchange.user.domain.AuthCredential;
import open.vincentf13.exchange.user.domain.AuthCredentialType;
import org.apache.ibatis.annotations.Param;

public interface AuthCredentialMapper {

    void insert(AuthCredential credential);

    AuthCredential findByUserIdAndType(@Param("userId") Long userId,
                                       @Param("credentialType") AuthCredentialType credentialType);
}
