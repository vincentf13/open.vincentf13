package open.vincentf13.exchange.user.infra.persistence.mapper;

import open.vincentf13.exchange.user.domain.model.UserStatus;
import open.vincentf13.exchange.user.infra.persistence.po.UserPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    boolean existsByEmail(@Param("email") String email);

    void insert(UserPO user);

    UserPO findById(@Param("id") Long id);

    UserPO findByEmail(@Param("email") String email);

    void updateStatus(@Param("id") Long id, @Param("status") UserStatus status);

    List<UserPO> findAll();
}
