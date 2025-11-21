package open.vincentf13.exchange.user.infra.persistence.mapper;

import open.vincentf13.exchange.user.infra.persistence.po.UserPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    void insertSelective(UserPO user);

    List<UserPO> findBy(UserPO user);

    void updateSelective(UserPO user);

    void batchInsert(@Param("list") List<UserPO> users);

    void batchUpdate(@Param("list") List<UserPO> users);
}
