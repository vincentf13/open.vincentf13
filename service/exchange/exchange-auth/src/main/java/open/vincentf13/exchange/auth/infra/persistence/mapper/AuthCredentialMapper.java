package open.vincentf13.exchange.auth.infra.persistence.mapper;

import open.vincentf13.exchange.auth.infra.persistence.po.AuthCredentialPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AuthCredentialMapper {

    int insertSelective(AuthCredentialPO credential);

    List<AuthCredentialPO> findBy(AuthCredentialPO credential);

    int updateSelectiveBy(@Param("record") AuthCredentialPO record,
                          @Param("id") Long id,
                          @Param("userId") Long userId,
                          @Param("expectedVersion") Integer expectedVersion,
                          @Param("currentStatus") String currentStatus);

    void batchInsert(@Param("list") List<AuthCredentialPO> credentials);
}
