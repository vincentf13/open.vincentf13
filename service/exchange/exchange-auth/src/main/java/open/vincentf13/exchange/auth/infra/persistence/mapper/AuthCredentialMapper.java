package open.vincentf13.exchange.auth.infra.persistence.mapper;

import open.vincentf13.exchange.auth.infra.persistence.po.AuthCredentialPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AuthCredentialMapper {

    int insertSelective(AuthCredentialPO credential);

    List<AuthCredentialPO> findBy(AuthCredentialPO credential);

    int updateStatusByIdAndVersion(@Param("id") Long id,
                                   @Param("status") String status,
                                   @Param("expectedVersion") Integer expectedVersion);

    void batchInsert(@Param("list") List<AuthCredentialPO> credentials);

    void batchUpdate(@Param("list") List<AuthCredentialPO> credentials);
}
