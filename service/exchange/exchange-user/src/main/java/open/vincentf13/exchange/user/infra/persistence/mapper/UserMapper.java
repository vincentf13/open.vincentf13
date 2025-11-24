package open.vincentf13.exchange.user.infra.persistence.mapper;

import open.vincentf13.exchange.user.infra.persistence.po.UserPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    int insertSelective(UserPO user);

    List<UserPO> findBy(UserPO user);

    int updateSelectiveBy(@Param("record") UserPO record,
                          @Param("id") Long id,
                          @Param("externalId") String externalId);

    void batchInsert(@Param("list") List<UserPO> users);
}
