package open.vincentf13.exchange.user.infra.persistence.mapper;

import open.vincentf13.exchange.user.infra.persistence.po.UserPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    int insertSelective(UserPO user);

    List<UserPO> findBy(UserPO user);

    int updateStatusById(@Param("id") Long id, @Param("status") String status);

    void batchInsert(@Param("list") List<UserPO> users);

    void batchUpdate(@Param("list") List<UserPO> users);
}
