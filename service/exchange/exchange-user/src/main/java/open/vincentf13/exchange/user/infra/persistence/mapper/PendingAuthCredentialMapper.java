package open.vincentf13.exchange.user.infra.persistence.mapper;

import open.vincentf13.exchange.auth.api.dto.AuthCredentialType;
import open.vincentf13.exchange.user.infra.persistence.po.PendingAuthCredentialPO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface PendingAuthCredentialMapper {

    void insert(PendingAuthCredentialPO credential);

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

    List<PendingAuthCredentialPO> findByStatus(@Param("status") String status,
                                               @Param("limit") int limit);
}
