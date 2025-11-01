package open.vincentf13.exchange.user.infra.persistence.mapper;

import open.vincentf13.exchange.auth.sdk.rest.api.dto.AuthCredentialType;
import open.vincentf13.exchange.user.infra.persistence.po.AuthCredentialPendingPO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface AuthCredentialPendingMapper {

    void insert(AuthCredentialPendingPO credential);

    void markCompleted(@Param("userId") Long userId,
                       @Param("credentialType") AuthCredentialType credentialType,
                       @Param("status") String status,
                       @Param("updatedAt") Instant updatedAt);

    void markFailure(@Param("userId") Long userId,
                     @Param("credentialType") AuthCredentialType credentialType,
                     @Param("status") String status,
                     @Param("lastError") String lastError,
                     @Param("nextRetryAt") Instant nextRetryAt,
                     @Param("updatedAt") Instant updatedAt);

    List<AuthCredentialPendingPO> findReady(@Param("status") String status,
                                            @Param("now") Instant now,
                                            @Param("limit") int limit);
}
